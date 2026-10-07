// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: parser preservation, bounds and path regression tests.
package commandbulkload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BatchParserTest {
    @TempDir Path root;
    private final BatchParser parser = new BatchParser();
    private BatchParser.Batch read(String content) throws IOException {
        Files.writeString(root.resolve("batch.cbl"), content);
        return parser.read(root, "batch.cbl", 1048576, 10000);
    }
    @Test void preservesArgumentsDuplicatesAndPhysicalLines() throws Exception {
        var batch = read("\uFEFF# Header\r\n\r\n > /lp group vip permission set modifyworld.* true world=stadt\r\n"
                + "say Grüß Gott \"1234\" # not a comment\n/ say Grüß Gott \"1234\" # not a comment\n> # comment\n");
        assertEquals(List.of(3, 4, 5), batch.commands().stream().map(BatchParser.CommandLine::line).toList());
        assertEquals(List.of("lp group vip permission set modifyworld.* true world=stadt", "say Grüß Gott \"1234\" # not a comment",
                "say Grüß Gott \"1234\" # not a comment"), batch.commands().stream().map(BatchParser.CommandLine::command).toList());
        assertEquals(64, batch.sha256().length());
        assertThrows(UnsupportedOperationException.class, () -> batch.commands().clear());
        Files.writeString(root.resolve("batch.cbl"), "changed");
        assertTrue(batch.commands().getFirst().command().startsWith("lp "));
    }
    @ParameterizedTest @ValueSource(strings = {"../batch.cbl", "/batch.cbl", "folder/batch.cbl", "folder\\batch.cbl", "bad name.cbl", "batch.yml", "batch.txt", "..", ""})
    void rejectsPathsAndUnsupportedNames(String name) {
        assertThrows(IOException.class, () -> parser.read(root, name, 100, 10));
    }
    @ParameterizedTest @ValueSource(strings = {"", "# comment\n\n", "/\n", "say \u0000oops", "say\tbad"})
    void rejectsEmptyOrControlCharacterCommands(String content) {
        assertThrows(IOException.class, () -> read(content));
    }
    @Test void preservesFileAndRejectsSizeAndCommandCount() throws Exception {
        var path = root.resolve("batch.cbl");
        Files.writeString(path, "say one\nsay two\n");
        byte[] original = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> parser.read(root, "batch.cbl", 5, 10));
        assertThrows(IOException.class, () -> parser.read(root, "batch.cbl", 100, 1));
        assertArrayEquals(original, Files.readAllBytes(path));
    }
    @Test void rejectsMissingFilesDirectoriesAndLinks() throws Exception {
        assertThrows(IOException.class, () -> parser.read(root, "missing.cbl", 100, 10));
        Files.createDirectory(root.resolve("folder.cbl"));
        assertThrows(IOException.class, () -> parser.read(root, "folder.cbl", 100, 10));
        Path outside = Files.createTempFile("batch-outside-", ".cbl");
        try {
            Files.writeString(outside, "say test");
            Files.createSymbolicLink(root.resolve("link.cbl"), outside);
            assertThrows(IOException.class, () -> parser.read(root, "link.cbl", 100, 10));
        } finally { Files.deleteIfExists(outside); }
    }
    @Test void rejectsMalformedUtf8AndAllowsExactLimits() throws Exception {
        Files.write(root.resolve("batch.cbl"), new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThrows(IOException.class, () -> parser.read(root, "batch.cbl", 100, 1));
        Files.writeString(root.resolve("batch.cbl"), "say hi");
        assertEquals(1, parser.read(root, "batch.cbl", 6, 1).commands().size());
    }
}
