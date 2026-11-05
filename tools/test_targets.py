#!/usr/bin/env python3
"""Tests Blockpilot on every target, or the ones named, end to end.

    python3 tools/test_targets.py [--targets 26.3-fabric,26.2-neoforge] [--list]

One target at a time, from those in settings.gradle.kts: builds it with its unit tests, checks its jar, starts its dev
client, runs tools/smoke_test.py in text and JSON mode, closes the client and checks it shut down. JSON shapes must
match those saved from the other targets of its loader; the vanilla targets count as Fabric, whose replies they share.

Ends with a table of the targets and exits non-zero if any assertion failed, naming the target and the assertion. A
failure doesn't stop the targets after it. Logs and shapes are in build/test-targets/<target>/.

A full run takes about 25 minutes. Leave the game windows alone meanwhile: real input takes control back from the test.
Only the client this script started is ever stopped.
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
GRADLE = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
LOADERS = ("fabric", "neoforge", "vanilla")
METADATA = {"fabric": "fabric.mod.json", "neoforge": "META-INF/neoforge.mods.toml"}
START_SECONDS = 600
STOP_SECONDS = 120


class Result:
    def __init__(self, target):
        self.target = target
        self.problems = []
        self.passed = 0
        self.skipped = []

    def check(self, name, problem):
        """Records one assertion: its problem, or None when it held."""
        if problem:
            self.problems.append((name, problem))
        else:
            self.passed += 1

    def summary(self):
        total = self.passed + len(self.problems)
        return f"{self.passed}/{total}" + (f", {len(self.skipped)} skipped" if self.skipped else "")


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


def node_dir(target):
    return os.path.join(ROOT, "blockpilot", "versions", target)


def last_lines(path, pattern, count=6):
    """The last lines of a log that say what went wrong."""
    with open(path, encoding="utf-8", errors="replace") as source:
        matching = [line.strip() for line in source if re.search(pattern, line)]
    return " | ".join(line[:200] for line in matching[-count:]) or f"see {path}"


def check_jar(target, minecraft, loader, result):
    """The jar's name, what it holds and what it declares; none of this needs the game."""
    mod_version = properties(os.path.join(ROOT, "blockpilot", "gradle.properties"))["mod_version"]
    path = os.path.join(ROOT, "build", "libs", f"blockpilot-{mod_version}+{minecraft}-{loader}.jar")
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


def wait_for_client(gradle, game_dir, log):
    """Waits until the client answers with a screen up; returns why it didn't, or None."""
    launcher = os.path.join(game_dir, "blockpilot", "endpoint.json")
    deadline = time.monotonic() + START_SECONDS
    while time.monotonic() < deadline:
        if gradle.poll() is not None:
            return "the client stopped before opening its endpoint: " \
                   + last_lines(log, r"Mixin|Exception|Error|FAILED|What went wrong")
        if os.path.exists(launcher):
            code, output = smoke_test.run(game_dir, "screen", timeout=10)
            if code == 0 and re.match(r"ok (?!none)\S+", output) and not output.startswith("ok loading"):
                return None
        time.sleep(3)
    return f"no answer within {START_SECONDS} s: " + last_lines(log, r"Mixin|Exception|Error|FAILED")


def run_smoke_test(game_dir, directory, as_json, result):
    """Runs the smoke test and takes its assertions into the result."""
    mode = "json" if as_json else "text"
    command = [sys.executable, os.path.join(ROOT, "tools", "smoke_test.py"), "--game-dir", game_dir]
    command += ["--json"] if as_json else ["--shapes", os.path.join(directory, "shapes.txt")]
    log = os.path.join(directory, f"smoke-{mode}.log")
    with open(log, "w", encoding="utf-8") as output:
        done = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, text=True, check=False)
    with open(log, encoding="utf-8") as source:
        text = source.read()
    for name in re.findall(r"^pass (.+)$", text, re.MULTILINE):
        result.check(f"{mode}: {name}", None)
    failures = re.findall(r"^FAIL ([^:\n]+): ?(.*)$", text, re.MULTILINE)
    for name, problem in failures:
        result.check(f"{mode}: {name}", problem or f"see {log}")
    result.skipped += [f"{mode}: {name}: {reason}" for name, reason in re.findall(r"^skip ([^:\n]+): (.*)$", text,
                                                                                 re.MULTILINE)]
    if done.returncode != 0 and not failures:
        result.check(f"{mode}: smoke test", f"exit {done.returncode}; see {log}")


def stop_client(gradle, game_dir):
    """Closes the client with `quit`; only if that fails, stops it: the build it runs under, then its process."""
    pid = game_pid(game_dir)
    if os.path.exists(os.path.join(game_dir, "blockpilot", "endpoint.json")):
        smoke_test.run(game_dir, "quit", timeout=60)
    try:
        gradle.wait(timeout=STOP_SECONDS)
        return None
    except subprocess.TimeoutExpired:
        gradle.send_signal(signal.SIGINT)
    try:
        gradle.wait(timeout=30)
    except subprocess.TimeoutExpired:
        gradle.kill()
    if pid:
        try:
            os.kill(pid, signal.SIGTERM)
        except OSError:
            pass
    return f"it didn't close within {STOP_SECONDS} s of quit"


def game_pid(game_dir):
    try:
        with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as source:
            return json.load(source)["pid"]
    except (OSError, ValueError, KeyError):
        return None


def test_target(target, result):
    minecraft = properties(os.path.join(node_dir(target), "gradle.properties"))["minecraft_version"]
    loader = target.rsplit("-", 1)[1]
    directory = os.path.join(OUTPUT, target)
    os.makedirs(directory, exist_ok=True)

    log = os.path.join(directory, "build.log")
    with open(log, "w", encoding="utf-8") as output:
        built = subprocess.run([GRADLE, "--console=plain", f":blockpilot:{target}:build"], cwd=ROOT, stdout=output,
                               stderr=subprocess.STDOUT, check=False)
    result.check("build and unit tests",
                 None if built.returncode == 0 else last_lines(log, r"error:|FAILED|What went wrong", 8))
    jar = check_jar(target, minecraft, loader, result) if built.returncode == 0 else None
    if loader == "vanilla" and jar:
        check_agent_refuses_other_versions(jar, minecraft, result)
    if built.returncode != 0:
        return

    game_dir = os.path.join(node_dir(target), "run")
    endpoint = os.path.join(game_dir, "blockpilot", "endpoint.json")
    if os.path.exists(endpoint):
        os.remove(endpoint)
    if sys.platform == "darwin":
        # A client started while the display sleeps fails with "Failed to locate a primary monitor".
        subprocess.Popen(["caffeinate", "-u", "-t", "300"])
    log = os.path.join(directory, "client.log")
    with open(log, "w", encoding="utf-8") as output:
        gradle = subprocess.Popen([GRADLE, "--console=plain", f":blockpilot:{target}:runClient"], cwd=ROOT,
                                  stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    problem = "interrupted"
    try:
        problem = wait_for_client(gradle, game_dir, log)
        result.check("client starts with every mixin applied", problem)
        if not problem:
            run_smoke_test(game_dir, directory, False, result)
            run_smoke_test(game_dir, directory, True, result)
    finally:
        # Gradle has a session of its own, so an interrupt doesn't reach the client it runs.
        stopped = stop_client(gradle, game_dir)
    result.check("client closes on quit", stopped)
    if not problem:
        # The shutdown hook removes the file, which nothing else does.
        result.check("the shutdown hook ran", f"{endpoint} is still there" if os.path.exists(endpoint) else None)


def compare_shapes(results):
    """Fails a target whose JSON shapes differ from those saved for another target of its loader."""
    saved = {target: os.path.join(OUTPUT, target, "shapes.txt") for target in configured_targets()}
    family = {target: target.rsplit("-", 1)[1].replace("vanilla", "fabric") for target in saved}
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
            result.check(f"JSON shapes match {other}", different[0][:400] if different else None)


def report(results):
    width = max(len(result.target) for result in results)
    print(f"\n{'target':{width}}  result  assertions")
    for result in results:
        print(f"{result.target:{width}}  {'FAIL' if result.problems else 'pass'}    {result.summary()}")
    for result in results:
        for name, problem in result.problems:
            print(f"FAIL {result.target}: {name}: {problem}")
        for skipped in result.skipped:
            print(f"skip {result.target}: {skipped}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--targets", help="comma-separated targets; all of them when left out")
    parser.add_argument("--list", action="store_true", help="print the targets and exit")
    args = parser.parse_args()
    configured = configured_targets()
    if args.list:
        print("\n".join(configured))
        return 0
    chosen = args.targets.split(",") if args.targets else configured
    unknown = [target for target in chosen if target not in configured]
    if unknown:
        parser.error(f"unknown targets {unknown}; these exist: {' '.join(configured)}")
    if shutil.which("java") is None:
        parser.error("java isn't on the PATH; the vanilla agent's checks need it")
    # A stop request ends the run as an interrupt does, which stops the client it started.
    signal.signal(signal.SIGTERM, signal.default_int_handler)
    results = []
    try:
        for target in chosen:
            print(f"testing {target}", flush=True)
            result = Result(target)
            try:
                test_target(target, result)
            except Exception as error:
                # A surprise in one target, such as a corrupt jar, mustn't lose the results of the others.
                result.check("the test itself", repr(error)[:400])
            results.append(result)
            print(f"  {'FAIL' if result.problems else 'pass'} {result.summary()}", flush=True)
    except KeyboardInterrupt:
        print("interrupted; the target under way is left out", flush=True)
        if results:
            report(results)
        return 130
    compare_shapes(results)
    report(results)
    return 1 if any(result.problems for result in results) else 0


if __name__ == "__main__":
    sys.exit(main())
