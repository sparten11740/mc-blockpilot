#!/usr/bin/env python3
"""Tests Blockpilot on every target, or the ones named, end to end.

    python3 tools/test_targets.py [--targets 26.3-fabric,26.2-neoforge] [--suite smoke|security|all] [--baritone]
        [--combine] [--list]

It refuses to start if blockpilot/versions or the release workflow's rows disagree with settings.gradle.kts.

One target at a time, from those in settings.gradle.kts: builds it with its unit tests, checks its jar, starts its dev
client, runs tools/smoke_test.py in text and JSON mode, then tools/security_smoke_test.py, closes the client and checks
it shut down. JSON shapes must match those saved from the other targets of its loader; the vanilla targets count as
Fabric, whose replies they share, and the Forge target as NeoForge. --suite smoke or --suite security runs only one of
the two suites (default all).

Each target's results go to build/test-targets/<target>/results.json once it is done. --combine starts no client: it
reads the results saved there, from this machine or copied from others, fails a target without any as unfinished, and
compares shapes and skips across the targets as one run would. CI runs one target per machine and combines them so.

--baritone starts each client with Baritone (-Pblockpilot.baritone, which fetches the build its gradle.properties
names) and runs only the smoke test's check of walks through it, on the targets that have a Baritone build. It doesn't
check that the client closes, as on 26.x Baritone keeps it from closing by itself.

Ends with a table of the targets and exits non-zero if any assertion failed, naming the target and the assertion. A
failure doesn't stop the targets after it. Logs, shapes and results are in build/test-targets/<target>/.

A full run takes about 25 minutes. Leave the game windows alone meanwhile: real input takes control back from the test.
Only the client this script started is ever stopped, though a client still loading has no endpoint to be seen by, so
don't start one by hand meanwhile. It runs on macOS and Linux.
"""
import argparse
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

import smoke_test

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUTPUT = os.path.join(ROOT, "build", "test-targets")
GRADLE = os.path.join(ROOT, "gradlew")
LOADERS = ("fabric", "forge", "neoforge", "vanilla")
METADATA = {"fabric": "fabric.mod.json", "forge": "META-INF/mods.toml", "neoforge": "META-INF/neoforge.mods.toml"}
SHAPE_FAMILY = {"vanilla": "fabric", "forge": "neoforge"}
START_SECONDS = 600
STOP_SECONDS = 120
TERM_SECONDS = 10
# A smoke test takes a minute or two; a hung client would otherwise cost three minutes for each of its many steps.
SMOKE_SECONDS = 600
STOP_SIGNALS = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)
RESULTS = "results.json"


class Result:
    def __init__(self, target):
        self.target = target
        self.problems = []
        self.held = []
        self.skipped = []

    def check(self, name, problem):
        """Records one assertion: its problem, or None when it held."""
        if problem:
            self.problems.append((name, problem))
        else:
            self.held.append(name)

    def summary(self):
        total = len(self.held) + len(self.problems)
        return f"{len(self.held)}/{total}" + (f", {len(self.skipped)} skipped" if self.skipped else "")


def configured_targets():
    """The nodes settings.gradle.kts declares, in its order."""
    with open(os.path.join(ROOT, "settings.gradle.kts"), encoding="utf-8") as source:
        settings = source.read()
    return [f"{minecraft}-{loader}"
            for minecraft, loaders in re.findall(r'^\s*target\("([^"]+)", (.*)\)$', settings, re.MULTILINE)
            for loader in re.findall(r'"(\w+)"', loaders)]


def properties(path):
    with open(path, encoding="utf-8") as source:
        return dict(line.strip().split("=", 1) for line in source if "=" in line and not line.startswith("#"))


def release_rows():
    """The (minecraft, loader) pairs the release uploads to CurseForge."""
    with open(os.path.join(ROOT, ".github", "workflows", "release.yml"), encoding="utf-8") as source:
        return set(re.findall(r"^\s*- \{ minecraft: '?([^,']+)'?, loader: (\w+),", source.read(), re.MULTILINE))


def target_list_problems(targets):
    """Where versions/ and the release's rows disagree with settings.gradle.kts, whose targets are what this tests."""
    versions = os.path.join(ROOT, "blockpilot", "versions")
    # A removed target's folder can stay behind holding only its ignored run and build output.
    folders = {name for name in os.listdir(versions)
               if os.path.exists(os.path.join(versions, name, "gradle.properties"))}
    problems = [f"{name} is in blockpilot/versions but not read from settings.gradle.kts; is its target( line wrapped?"
                for name in sorted(folders - set(targets))]
    problems += [f"{name} is in settings.gradle.kts but has no folder in blockpilot/versions"
                 for name in sorted(set(targets) - folders)]
    if problems:
        return problems
    expected = {(properties(os.path.join(node_dir(target), "gradle.properties"))["minecraft_version"],
                 target.rsplit("-", 1)[1])
                for target in targets if not target.endswith("-vanilla")}
    rows = release_rows()
    problems += [f"release.yml has no row for {minecraft} {loader}" for minecraft, loader in sorted(expected - rows)]
    problems += [f"release.yml has a row for {minecraft} {loader}, which isn't a target"
                 for minecraft, loader in sorted(rows - expected)]
    return problems


def node_dir(target):
    return os.path.join(ROOT, "blockpilot", "versions", target)


def last_lines(path, pattern, count=6):
    """The last lines of a log that say what went wrong."""
    with open(path, encoding="utf-8", errors="replace") as source:
        matching = [line.strip() for line in source if re.search(pattern, line)]
    return " | ".join(line[:200] for line in matching[-count:]) or f"see {path}"


def jar_path(minecraft, loader):
    mod_version = properties(os.path.join(ROOT, "blockpilot", "gradle.properties"))["mod_version"]
    return os.path.join(ROOT, "build", "libs", f"blockpilot-{mod_version}+{minecraft}-{loader}.jar")


def check_jar(minecraft, loader, result):
    """The jar's name, what it holds and what it declares; none of this needs the game."""
    mod_version = properties(os.path.join(ROOT, "blockpilot", "gradle.properties"))["mod_version"]
    path = jar_path(minecraft, loader)
    if not os.path.exists(path):
        result.check("jar name", f"no {os.path.basename(path)} in build/libs")
        return None
    result.check("jar name", None)
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        others = [other for other in LOADERS if other != loader
                  and any(name.startswith(f"dev/blockpilot/platform/{other}/") for name in names)]
        result.check("jar has only its own loader's classes", f"has {others}" if others else None)
        if loader == "vanilla":
            manifest = jar.read("META-INF/MANIFEST.MF").decode()
            wanted = ["Premain-Class: dev.blockpilot.platform.vanilla.BlockpilotAgent",
                      f"Implementation-Version: {mod_version}", f"Blockpilot-Minecraft: {minecraft}"]
            absent = [line for line in wanted if line not in manifest]
            result.check("vanilla manifest names the agent and its versions", f"missing {absent}" if absent else None)
            stray = [name for name in METADATA.values() if name in names]
            result.check("vanilla jar has no loader metadata", f"has {stray}" if stray else None)
        else:
            metadata = jar.read(METADATA[loader]).decode() if METADATA[loader] in names else ""
            pinned = f'"minecraft": "{minecraft}"' if loader == "fabric" else f'versionRange="[{minecraft}]"'
            result.check("loader metadata pins the game version",
                         None if pinned in metadata else f"no {pinned} in {METADATA[loader]}")
        if loader == "forge":
            # Forge 1.20.1 runs mods with intermediate names: mixins need their refmap, and Forge finds the mixin
            # configs only in the manifest.
            manifest = jar.read("META-INF/MANIFEST.MF").decode()
            wanted = [name for name in ("blockpilot.refmap.json",) if name not in names]
            result.check("forge jar has its refmap and lists its mixins",
                         f"missing {wanted}" if wanted else None if "MixinConfigs: blockpilot.mixins.json" in manifest
                         else "no MixinConfigs in the manifest")
        # 26.3 has no GLFW at all, so a reference would fail when that class loads, not at build time.
        if tuple(int(part) for part in minecraft.split(".")) >= (26, 3):
            glfw = [name for name in names if name.endswith(".class") and b"org/lwjgl/glfw" in jar.read(name)]
            result.check("jar doesn't reference GLFW", f"referenced in {glfw[:3]}" if glfw else None)
    return path


def check_agent_refuses_other_versions(jar, minecraft, result):
    """The agent reads the game's version.json before anything else, so a directory with only that file is enough."""
    with tempfile.TemporaryDirectory() as game:
        with open(os.path.join(game, "version.json"), "w", encoding="utf-8") as file:
            json.dump({"id": "0.0"}, file)
        done = subprocess.run(["java", f"-javaagent:{jar}", "-cp", game, "-version"], capture_output=True, text=True,
                              timeout=60)
    wanted = f"This agent runs on Minecraft {minecraft} only, not 0.0"
    result.check("the agent refuses another version", None if wanted in done.stdout + done.stderr
                 else f"no {wanted!r}: {(done.stdout + done.stderr).strip()[:300]}")


def has_a_screen_up(game_dir):
    try:
        code, output = smoke_test.run(game_dir, "screen", timeout=10)
    except (OSError, subprocess.SubprocessError):
        return False
    return code == 0 and bool(re.match(r"ok (?!none)\S+", output)) and not output.startswith("ok loading")


def wait_for_client(gradle, game_dir, log):
    """Waits until the client answers with a screen up; returns why it didn't, or None."""
    endpoint = os.path.join(game_dir, "blockpilot", "endpoint.json")
    deadline = time.monotonic() + START_SECONDS
    while time.monotonic() < deadline:
        if gradle.poll() is not None:
            return "the client stopped before showing a screen: " \
                   + last_lines(log, r"Mixin|Exception|Error|FAILED|What went wrong")
        if os.path.exists(endpoint) and has_a_screen_up(game_dir):
            return None
        time.sleep(3)
    return f"no screen within {START_SECONDS} s: " + last_lines(log, r"Mixin|Exception|Error|FAILED")


def smoke_arguments(mode, directory):
    """What the smoke test is given in each mode: text saves the JSON shapes, baritone runs only Baritone's check."""
    return {"text": ["--shapes", os.path.join(directory, "shapes.txt")], "json": ["--json"],
            "baritone": ["--baritone"], "security": []}[mode]


def run_smoke_test(game_dir, directory, mode, result):
    """Runs a suite (a smoke test mode, or security) and takes its assertions into the result; returns whether it
    finished."""
    script = "security_smoke_test.py" if mode == "security" else "smoke_test.py"
    command = [sys.executable, "-u", os.path.join(ROOT, "tools", script), "--game-dir", game_dir]
    command += smoke_arguments(mode, directory)
    log = os.path.join(directory, f"smoke-{mode}.log")
    returncode = None
    with open(log, "w", encoding="utf-8") as output:
        try:
            returncode = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, text=True, check=False,
                                        timeout=SMOKE_SECONDS).returncode
        except subprocess.TimeoutExpired:
            pass
    with open(log, encoding="utf-8") as source:
        text = source.read()
    for name in re.findall(r"^pass (.+)$", text, re.MULTILINE):
        result.check(f"{mode}: {name}", None)
    failures = re.findall(r"^FAIL ([^:\n]+): ?(.*)$", text, re.MULTILINE)
    for name, problem in failures:
        result.check(f"{mode}: {name}", problem or f"see {log}")
    result.skipped += [(f"{mode}: {name}", reason) for name, reason in re.findall(r"^skip ([^:\n]+): (.*)$", text,
                                                                                  re.MULTILINE)]
    if returncode is None:
        result.check(f"{mode}: smoke test", f"it didn't finish within {SMOKE_SECONDS} s; see {log}")
    elif returncode != 0 and not failures:
        result.check(f"{mode}: smoke test", f"exit {returncode}; see {log}")
    return returncode is not None


def quit_client(gradle, game_dir):
    """Asks a client to close; returns why it didn't, or None."""
    try:
        code, output = smoke_test.run(game_dir, "quit", timeout=60)
    except (OSError, subprocess.SubprocessError) as error:
        return f"quit failed: {error}"
    if code != 0:
        return f"quit failed: {output.strip()[:200]}"
    try:
        status = gradle.wait(timeout=STOP_SECONDS)
    except subprocess.TimeoutExpired:
        return f"it didn't close within {STOP_SECONDS} s of quit"
    return None if status == 0 else f"the build running it ended with {status}"


def force_stop(gradle, game_dir):
    """Stops a client through the build running it, then its process if its endpoint still names it. Nothing interrupts
    this, as that would leave the game running; it takes a minute at most."""
    handlers = [(number, signal.signal(number, signal.SIG_IGN)) for number in STOP_SIGNALS]
    try:
        gradle.send_signal(signal.SIGINT)
        try:
            gradle.wait(timeout=30)
        except subprocess.TimeoutExpired:
            gradle.kill()
        # Read only now: closing removes the file, and the process's id could then be another program's.
        pid = game_pid(game_dir)
        if pid and alive(pid):
            os.kill(pid, signal.SIGTERM)
            deadline = time.monotonic() + TERM_SECONDS
            while alive(pid) and time.monotonic() < deadline:
                time.sleep(0.5)
            if alive(pid):
                os.kill(pid, signal.SIGKILL)
    finally:
        for number, handler in handlers:
            signal.signal(number, handler)


def stop_client(gradle, game_dir, answered):
    """Closes a client that answered with `quit`, and stops one that didn't, or doesn't close. An interrupt meanwhile
    goes straight to stopping it, then on up. Returns why a client that answered didn't close, or None."""
    problem = None
    interrupted = False
    if answered:
        try:
            problem = quit_client(gradle, game_dir)
            if not problem:
                return None
        except KeyboardInterrupt:
            interrupted = True
    force_stop(gradle, game_dir)
    if interrupted:
        raise KeyboardInterrupt
    return problem


def game_pid(game_dir):
    try:
        with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as source:
            return json.load(source)["pid"]
    except (OSError, ValueError, KeyError):
        return None


def alive(pid):
    """Whether that process can be a game: another program may have the id of one long gone."""
    name = subprocess.run(["ps", "-o", "comm=", "-p", str(pid)], capture_output=True, text=True, check=False).stdout
    return "java" in name


def has_baritone(target):
    """Whether the target names a Baritone build to fetch, as the NeoForge and Fabric targets do."""
    return "baritone_version" in properties(os.path.join(node_dir(target), "gradle.properties"))


def test_target(target, result, baritone, suite="all"):
    minecraft = properties(os.path.join(node_dir(target), "gradle.properties"))["minecraft_version"]
    loader = target.rsplit("-", 1)[1]
    directory = os.path.join(OUTPUT, target)
    os.makedirs(directory, exist_ok=True)

    game_dir = os.path.join(node_dir(target), "run")
    endpoint = os.path.join(game_dir, "blockpilot", "endpoint.json")
    # First of all: a running vanilla client runs from the jar the next lines delete.
    running = game_pid(game_dir)
    if running and alive(running):
        result.check("no client is running there already", f"process {running} has {endpoint}; close it first")
        return
    # What an earlier build or run left would pass for this one's.
    for leftover in [jar_path(minecraft, loader), os.path.join(directory, "shapes.txt")]:
        if os.path.exists(leftover):
            os.remove(leftover)
    log = os.path.join(directory, "build.log")
    with open(log, "w", encoding="utf-8") as output:
        built = subprocess.run([GRADLE, "--console=plain", f":blockpilot:{target}:build"], cwd=ROOT, stdout=output,
                               stderr=subprocess.STDOUT, check=False)
    result.check("build and unit tests",
                 None if built.returncode == 0 else last_lines(log, r"error:|FAILED|What went wrong", 8))
    jar = check_jar(minecraft, loader, result) if built.returncode == 0 else None
    if loader == "vanilla" and jar:
        check_agent_refuses_other_versions(jar, minecraft, result)
    if built.returncode != 0:
        return

    if os.path.exists(endpoint):
        os.remove(endpoint)
    # A client started while the display sleeps fails with "Failed to locate a primary monitor".
    awake = subprocess.Popen(["caffeinate", "-u", "-t", "300"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) \
        if sys.platform == "darwin" else None
    try:
        log = os.path.join(directory, "client.log")
        with open(log, "w", encoding="utf-8") as output:
            # devcheck:ping stands in for another mod's command in the security suite's allowedExtensions check.
            gradle = subprocess.Popen([GRADLE, "--console=plain", f":blockpilot:{target}:runClient",
                                       "-Pblockpilot.devcheck=true"]
                                      + (["-Pblockpilot.baritone"] if baritone else []), cwd=ROOT,
                                      stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        answered = False
        try:
            problem = wait_for_client(gradle, game_dir, log)
            answered = problem is None
            # Mixins apply as their target classes load, so a stale one in a world shows in the smoke test instead.
            result.check("client reaches a screen", problem)
            modes = ["baritone"] if baritone else (["text", "json"] if suite in ("smoke", "all") else []) \
                + (["security"] if suite in ("security", "all") else [])
            for mode in modes:
                if not answered or not run_smoke_test(game_dir, directory, mode, result):
                    break
        finally:
            # Gradle has a session of its own, so an interrupt doesn't reach the client it runs.
            stopped = stop_client(gradle, game_dir, answered)
    finally:
        if awake:
            awake.terminate()
    # On 26.x, Baritone's own threads outlive quit, with or without a walk, until the game's shutdown watchdog ends
    # it; the regular runs check closing.
    if answered and not baritone:
        result.check("client closes on quit", stopped)
        # The shutdown hook removes the file, which nothing else does.
        result.check("the shutdown hook ran", f"{endpoint} is still there" if os.path.exists(endpoint) else None)


def compare_shapes(results):
    """Fails a target whose JSON shapes differ from those saved for another target of its loader."""
    saved = {target: os.path.join(OUTPUT, target, "shapes.txt") for target in configured_targets()}
    # The vanilla targets answer as Fabric does, and Forge as NeoForge does.
    family = {target: SHAPE_FAMILY.get(target.rsplit("-", 1)[1], target.rsplit("-", 1)[1]) for target in saved}
    for result in results:
        mine = saved[result.target]
        if not os.path.exists(mine):
            continue
        with open(mine, encoding="utf-8") as source:
            lines = source.read().splitlines()
        for other, path in saved.items():
            if other == result.target or family[other] != family[result.target] or not os.path.exists(path):
                continue
            with open(path, encoding="utf-8") as source:
                theirs = source.read().splitlines()
            different = [f"{there} on {other}, here {here}" for here, there in zip(lines, theirs) if here != there]
            if len(lines) != len(theirs):
                different.append(f"{len(theirs)} queries on {other}, here {len(lines)}")
            result.check(f"JSON shapes match {other}", different[0][:400] if different else None)


def compare_skips(results):
    """Fails a target that skipped what another ran: a machine's lack would stop both, so this target has broken it."""
    for result in results:
        for name, reason in result.skipped:
            ran_on = [other.target for other in results if name in other.held]
            if ran_on:
                result.check(f"{name} runs here too", f"it ran on {ran_on[0]}, but here: {reason}")


def results_path(target):
    return os.path.join(OUTPUT, target, RESULTS)


def save_results(result):
    with open(results_path(result.target), "w", encoding="utf-8") as output:
        json.dump({"target": result.target, "held": result.held,
                   "failed": [{"name": name, "problem": problem} for name, problem in result.problems],
                   "skipped": [{"name": name, "reason": reason} for name, reason in result.skipped]}, output, indent=1)


def load_results(target):
    """A target's saved results; a target without them fails, as its run never finished."""
    result = Result(target)
    path = results_path(target)
    try:
        with open(path, encoding="utf-8") as source:
            saved = json.load(source)
        result.held = list(saved["held"])
        result.problems = [(failed["name"], failed["problem"]) for failed in saved["failed"]]
        result.skipped = [(skipped["name"], skipped["reason"]) for skipped in saved["skipped"]]
    except FileNotFoundError:
        result.check("results saved", f"no {os.path.relpath(path, ROOT)}: its run didn't finish")
    except (ValueError, KeyError, TypeError) as error:
        result.check("results saved", f"{os.path.relpath(path, ROOT)} is unreadable: {error!r}"[:400])
    return result


def report(results):
    width = max(len(result.target) for result in results)
    print(f"\n{'target':{width}}  result  assertions")
    for result in results:
        print(f"{result.target:{width}}  {'FAIL' if result.problems else 'pass'}    {result.summary()}")
    for result in results:
        for name, problem in result.problems:
            print(f"FAIL {result.target}: {name}: {problem}")
        for name, reason in result.skipped:
            print(f"skip {result.target}: {name}: {reason}")


def interrupted(signal_number, frame):
    """Ends the run on Ctrl-C, a stop request or a closed terminal, which stops the client it started."""
    try:
        print("interrupted; closing the client under way", flush=True)
    except OSError:
        pass
    raise KeyboardInterrupt


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--targets", help="comma-separated targets; all of them when left out")
    parser.add_argument("--baritone", action="store_true",
                        help="start the clients with Baritone and run only the check of walks through it")
    parser.add_argument("--suite", choices=["smoke", "security", "all"], default="all",
                        help="run only the smoke test or only the security checks (default: both)")
    parser.add_argument("--list", action="store_true", help="print the targets and exit")
    parser.add_argument("--combine", action="store_true",
                        help="start no client: report on the results saved in build/test-targets/, as one run would")
    args = parser.parse_args()
    configured = configured_targets()
    problems = target_list_problems(configured)
    if problems:
        parser.error("the lists of targets disagree:\n  " + "\n  ".join(problems))
    if args.list:
        print("\n".join(configured))
        return 0
    chosen = args.targets.split(",") if args.targets else configured
    unknown = [target for target in chosen if target not in configured]
    if unknown:
        parser.error(f"unknown targets {unknown}; these exist: {' '.join(configured)}")
    if args.combine:
        if args.baritone:
            parser.error("--combine starts no client, so it takes no --baritone")
        return finish([load_results(target) for target in chosen])
    if args.baritone:
        without = [target for target in chosen if not has_baritone(target)]
        if args.targets and without:
            parser.error(f"{' '.join(without)} can't load Baritone; leave out --targets for all that can")
        chosen = [target for target in chosen if target not in without]
    if os.name == "nt":
        parser.error("this can't stop a client on Windows yet; run tools/smoke_test.py against one there")
    if shutil.which("java") is None:
        parser.error("java isn't on the PATH; the vanilla agent's checks need it")
    for number in STOP_SIGNALS:
        signal.signal(number, interrupted)
    results = []
    try:
        for target in chosen:
            print(f"testing {target}", flush=True)
            result = Result(target)
            os.makedirs(os.path.join(OUTPUT, target), exist_ok=True)
            # An earlier run's results would stand in for this one's if this one never finished.
            if os.path.exists(results_path(target)):
                os.remove(results_path(target))
            try:
                test_target(target, result, args.baritone, args.suite)
            except Exception as error:
                # A surprise in one target, such as a corrupt jar, mustn't lose the results of the others.
                result.check("the test itself", repr(error)[:400])
            save_results(result)
            results.append(result)
            print(f"  {'FAIL' if result.problems else 'pass'} {result.summary()}", flush=True)
    except KeyboardInterrupt:
        print("the target under way is left out", flush=True)
        if results:
            report(results)
        return 130
    return finish(results)


def finish(results):
    """Applies the checks across targets, prints the table and returns the exit code."""
    compare_shapes(results)
    compare_skips(results)
    report(results)
    return 1 if any(result.problems for result in results) else 0


if __name__ == "__main__":
    sys.exit(main())
