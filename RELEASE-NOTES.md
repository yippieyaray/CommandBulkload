# CommandBulkload 1.0.0

First final release for private use on Paper 26.2 with Java 25.

- Check and run UTF-8 `.cbl` files from `plugins/CommandBulkload/Uploads/`.
- Dispatch ordinary server commands in order as the console, with configurable
  interval and limits, status reporting and cancellation.
- Allow console/RCON access and players with OP status or `commandbulkload.command`.
- Log commands before dispatch in the main server log, with green prefix, aqua
  line numbers and white commands. Name the input file in the start message.
- Default to English; support seven languages and custom `own` messages with
  English fallback and backups when migrating unchanged standard messages.
- Provide colored client feedback. Preserve existing configuration and old log files.

Local verification on 2026-10-07: 84 tests passed, no failures, errors or skipped
tests. The owner reports stable server tests; no independent live-server test was
performed by the coding agent. No automatic retry, rollback or resume is provided.
Accepted dispatch does not prove completion inside the target plugin.

# CommandBulkload 1.0.0-BETA.2

- Default to English for new configurations; preserve existing language settings.
- Color client messages and document custom `own` language files.
- Log commands in the main server log before dispatch, with green prefix, aqua
  line number and white command. Name the file in the start message only.
- Remove separate run logs and gzip compression; leave existing logs intact.
- Migrate unchanged standard messages with `.bak` backups; preserve custom text.

Local BETA.2 development checkpoint: 84 tests passed; no BETA.2 tag was created.

# CommandBulkload 1.0.0-BETA.1

Initial private beta for Paper 26.2 and Java 25.

- Check and run UTF-8 command files from plugins/CommandBulkload/Uploads/, preserving command order.
- Dispatch one console command per scheduled step, with configurable limits and interval.
- Console/RCON access and OP or LuckPerms-authorized player access through
  commandbulkload.command; stop on dispatch failure or exception.
- Keep input files intact; no automatic retries, rollback or restart/resume.
- Record per-run audit trails under logs/CommandBulkload/ and report dispatch counts without claiming target-plugin completion.
- Green startup confirmation and concise progress/completion messages; unchanged legacy message lines are migrated with backups.
- Seven editable languages and own-file support, with English fallback.
- Validated configuration with support for independent future migration steps.

Automated tests are run through clean verify. Live-server validation is pending.
BETA.1 source and tag were uploaded; no binary GitHub release was published.


Local verification on 2026-10-07: 83 automated tests passed with
`./build.sh -B -o clean verify` (0 failures, 0 errors, 0 skipped).
