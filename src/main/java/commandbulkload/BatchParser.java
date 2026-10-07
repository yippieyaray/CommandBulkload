// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: bounded UTF-8 parsing of immutable console command batches from the Uploads directory.
package commandbulkload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class BatchParser {
    public record CommandLine(int line, String command) { }
    public record Batch(String filename, String sha256, List<CommandLine> commands) {
        public Batch { commands = List.copyOf(commands); }
    }

    public Batch read(Path uploadsDirectory, String filename, long maxBytes, int maxCommands) throws IOException {
        if (!filename.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.cbl")) {
            throw new IOException("Use a plain .cbl filename without directories or absolute paths.");
        }
        Path root = uploadsDirectory.toRealPath();
        Path input = root.resolve(filename);
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File is missing, not regular, or is a symbolic link: " + filename);
        }
        // Resolve the actual file as well as the parent; reject links replaced between checks.
        Path real = input.toRealPath();
        if (!real.getParent().equals(root)) throw new IOException("File is outside the Uploads directory.");
        if (Files.size(real) > maxBytes) throw new IOException("File exceeds max-file-bytes.");
        byte[] bytes;
        try (var stream = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes(Math.toIntExact(maxBytes + 1));
        }
        if (bytes.length > maxBytes) throw new IOException("File exceeds max-file-bytes.");
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<CommandLine> commands = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String command = lines[i].strip();
            if (command.isEmpty() || command.startsWith("#")) continue;
            if (command.startsWith(">")) command = command.substring(1).strip();
            if (command.isEmpty() || command.startsWith("#")) continue;
            if (command.startsWith("/")) command = command.substring(1).strip();
            if (command.isEmpty() || command.codePoints().anyMatch(Character::isISOControl)) {
                throw new IOException("Invalid command at line " + (i + 1) + ".");
            }
            commands.add(new CommandLine(i + 1, command));
            if (commands.size() > maxCommands) throw new IOException("File exceeds max-commands at line " + (i + 1) + ".");
        }
        if (commands.isEmpty()) throw new IOException("File contains no commands.");
        try {
            return new Batch(filename, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), commands);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
