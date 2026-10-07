// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: per-run audit trails of dispatch attempts, not storage confirmations.
package commandbulkload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

final class RunJournal implements AutoCloseable {
    private final Path file;
    private final BufferedWriter writer;
    private boolean closed;
    private RunJournal(Path file, BufferedWriter writer) { this.file = file; this.writer = writer; }
    static RunJournal open(Path directory, BatchParser.Batch batch) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve("run-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".log");
        var journal = new RunJournal(path, Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW));
        try {
            journal.write("FILE " + batch.filename() + " SHA256 " + batch.sha256() + " COMMANDS " + batch.commands().size());
        } catch (RuntimeException failure) { journal.close(); throw failure; }
        return journal;
    }
    Path file() { return file; }
    void write(String value) {
        try { writer.write(Instant.now() + " " + value.replace('\n', ' ').replace('\r', ' ')); writer.newLine(); writer.flush(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public void close() throws IOException { if (!closed) { closed = true; writer.close(); } }
}
