# CommandBulkload

A private Paper plugin that loads server commands from a text file and dispatches
one command at a time through the server console. Useful when a hosting panel
accepts only individual console commands.

**1.0.0-BETA.1 — Paper 26.2, Java 25.** Compiled against Paper API
`26.2.build.130-stable`. No Spigot or Folia support claim. LuckPerms commands work
through the ordinary server console; no LuckPerms API dependency is required.

## Install
1. Stop the server
2. Place `CommandBulkload.jar` in `plugins/` 
3. Start server.


## Use

### - Command file upload

Upload a UTF-8 `.cbl` command file to `plugins/CommandBulkload/Uploads/` (created automatically). 

Content example:
```text
# City permissions
lp group builder permission set modifyworld.blocks.place.tnt false world=stadt
lp group player permission set modifyworld.items.use.air.* true world=stadt
```
### - Run command file

In the server console (without a leading slash), run:

```text
commandbulkload check <command-file>.cbl
commandbulkload run <command-file>.cbl
commandbulkload status
commandbulkload cancel
```

`check` validates file format and limits without dispatching any command. It does
not validate LuckPerms argument semantics. Server and RCON consoles can use these commands. In-game, use a leading slash:
`/commandbulkload run <command-file>.cbl`. 

Players must be OP or have `commandbulkload.command`, which covers check, run, status and cancel. Command
blocks are rejected. To grant access through LuckPerms:

```text
lp group admin permission set commandbulkload.command true
```

The permission allows console-level command execution; grant it only to trusted
administrators. These are ordinary Minecraft/plugin commands, not just LuckPerms
permission commands. For example, `say`, `version` and other installed-plugin
commands are supported. Files never execute operating-system commands.
Players receive progress and final reports while online and still authorized.
RCON receives immediate replies; asynchronous reports appear in the server console
and audit log.

Blank lines and full-line `#` comments are ignored. A leading `/` or Markdown `>`
is accepted. Arguments, quotes, wildcards, inline `#` characters and repeated
commands are preserved. The original line numbers appear in failures and logs.
Use plain ASCII filenames such as `city-permissions.cbl`; paths, directories,
absolute filenames and symbolic links are rejected.

A file is fully read before execution and becomes an immutable batch. Only one
read or run can be active. Commands are dispatched on the server thread, one per
scheduled step. The first command runs on the next tick; subsequent commands
use the configured interval. Files are read off the server thread and never
modified or deleted.

Dispatch failure or an exception stops further commands. Cancellation and plugin
shutdown also stop further dispatches. There is no rollback, retry or automatic
restart/resume. Commands already dispatched can keep working in their target
plugin. **Accepted dispatch does not confirm that LuckPerms stored the change.**
An interval is a delay, not a completion acknowledgement. Inspect the target
plugin's console output and permissions after a run.

## Configuration and messages

Edit `plugins/CommandBulkload/config.yml` and restart:

| Setting | Default | Purpose |
| --- | --- | --- |
| `interval-ticks` | `20` | Delay between commands; 20 ticks is about one second at normal TPS |
| `max-file-bytes` | `1048576` | Input size limit (1 MiB) |
| `max-commands` | `10000` | Maximum commands in one file |
| `progress-every` | `25` | Progress message after this many command dispatches |
| `language` | `de` | `en`, `de`, `es`, `fr`, `pt_br`, `pl`, `tr` or `own` |

For custom messages, copy a bundled language to `lang/own.yml`, set `language: own`
and restart. Custom texts are preserved. Unchanged legacy progress and completion
texts are updated with numbered `.bak` backups. Missing entries fall back to bundled
selected-language texts, then English; `own` falls back to English. Every YAML
message must be single-line text. Keep named placeholders such as `{file}`,
`{total}`, `{dispatched}`, `{errors}`, `{remaining}` and `{reason}`. The `prefix`
entry controls `[CommandBulkload]`. Technical exception details and audit logs
remain English. Translation files have not received native-speaker review.

The managed `config-version` supports future independent migrations with fixed
numeric release thresholds. Leave it unchanged. Content-changing migrations save
`.bak` backups with numbered suffixes. Normal restarts do not repeat completed
steps. Version-only updates preserve other content without creating a backup.
The initial release has no legacy migrations. Invalid settings or active language
files stop plugin startup with a clear log message.

## Logs

Each run creates `logs/CommandBulkload/run-*.log` relative to the server main directory, recording input filename,
SHA-256, original line numbers, full commands and dispatch results. The end record
contains counts and completion/cancellation/failure state. Small audit records are
written and flushed after each dispatch. An audit write failure stops further
commands. Logs contain potentially sensitive command arguments; they remain on
your server and are not part of the repository or build artifacts.

## Build

```sh
./build.sh -B clean verify
# When dependencies are cached:
./build.sh -B -o clean verify
```

The script uses existing shared tools in `~/.local/share/minecraft-devtools/`:
JDK `jdk-25.0.4.1+1/Contents/Home` and Maven `apache-maven-3.9.11`. Overrides:
`COMMANDBULKLOAD_TOOLS_DIR`, `COMMANDBULKLOAD_JAVA_HOME`, `COMMANDBULKLOAD_MAVEN_HOME`.
No tools are downloaded or system Java settings changed. Maven normally uses
`~/.m2/repository`. Mockito is test-only and loads at test-JVM startup.

Artifacts: `target/CommandBulkload.jar` and `target/CommandBulkload-1.0.0-BETA.1.zip`.
The ZIP contains documentation, a harmless example and corresponding source.
Automated verification does not replace a live GPortal/Paper test.

## License

GPL-3.0-or-later. See LICENSE and NOTICE. Private project; no Hangar publication.


Local verification on 2026-10-07: 83 automated tests passed with
`./build.sh -B -o clean verify` (0 failures, 0 errors, 0 skipped).

`.cbl` stands for Command Bulk Load. The file remains plain UTF-8 text and can
be edited with any text editor. Rename existing `.txt` input files to `.cbl`.
