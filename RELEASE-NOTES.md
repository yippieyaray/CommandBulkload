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
This local beta is not published; commits, tags and pushes require explicit authorization.


Local verification on 2026-10-07: 83 automated tests passed with
`./build.sh -B -o clean verify` (0 failures, 0 errors, 0 skipped).
