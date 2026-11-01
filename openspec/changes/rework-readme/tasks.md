# Tasks

## 1. Documentation

- [ ] 1.1 Move the README's detailed sections to `docs/` (connecting, commands, command language, recording, Sable, dev environment, extending, building and releasing), with the command reference as one entry per command per group instead of table cells; verify that every fact of the old README is in exactly one docs file and that every docs file is linked from the README
- [x] 1.2 Use a generic `mymod` in the extension examples (`docs/extending.md`, `ReadmeSnippets` renamed `GuideSnippets`, `ExtensionApiTest`) and in the help examples of `create world`, `delete world` and `sounds match=`; verify the tests pass
- [ ] 1.3 Rewrite the README to about one page: what Blockpilot is, installing, the two ways to drive it (MCP and commands through the launcher), a short example, a table with one row per command, and links to `docs/`; verify the table lists every command `help` lists, and nothing else

## 2. Gallery

- [ ] 2.1 Capture gallery media in a world that shows the mod well: numbered screenshots and short animated GIFs in `docs/images/`, each at most 5 MiB with unique titles from their file names, and MP4 videos with sound kept out of git; verify the sizes and the titles `modrinth/gallery` would give them
- [ ] 2.2 Show one or two of the images in the README; verify they render on GitHub

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` and `openspec validate rework-readme --strict`; verify both pass
