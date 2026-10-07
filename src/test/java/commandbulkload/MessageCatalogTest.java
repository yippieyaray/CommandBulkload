// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: language completeness, preservation and single-pass substitution.
package commandbulkload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MessageCatalogTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings = {"en", "de", "es", "fr", "pt_br", "pl", "tr"})
    void everyLanguageIsCompleteAndKeepsPlaceholders(String code) throws Exception {
        var english = MessageCatalog.bundled("en");
        var selected = MessageCatalog.bundled(code);
        assertEquals(english.getKeys(false), selected.getKeys(false));
        Pattern placeholders = Pattern.compile("\\{[a-z-]+\\}");
        for (String key : english.getKeys(false)) {
            assertEquals(placeholders.matcher(english.getString(key)).results().map(m -> m.group()).sorted().toList(),
                    placeholders.matcher(selected.getString(key)).results().map(m -> m.group()).sorted().toList(), key);
        }
        var catalog = MessageCatalog.load(root, code);
        assertEquals("[CommandBulkload] ", catalog.prefix());
        for (String language : MessageCatalog.LANGUAGES) assertTrue(Files.exists(root.resolve("lang/" + language + ".yml")));
    }
    @Test void legacyStandardMessagesAreBackedUpOnceAndCustomTextIsPreserved() throws Exception {
        Files.createDirectories(root.resolve("lang"));
        Path file = root.resolve("lang/de.yml");
        String original = "# Keep\nprogress: '{file}: {attempted}/{total} versucht, {dispatched} abgesendet.'\nfinished: 'Custom {file}'\n";
        Files.writeString(file, original);
        var catalog = MessageCatalog.load(root, "de");
        assertEquals("batch.cbl: 25 von 475 abgesendet.", catalog.text("progress", Map.of("file", "batch.cbl", "dispatched", 25, "total", 475)));
        assertEquals("Custom batch.cbl", catalog.text("finished", Map.of("file", "batch.cbl")));
        assertEquals(original, Files.readString(root.resolve("lang/de.yml.bak")));
        assertTrue(Files.readString(file).startsWith("# Keep\n"));
        MessageCatalog.load(root, "de");
        assertFalse(Files.exists(root.resolve("lang/de.yml.bak.1")));
    }
    @Test void customOwnIsPreservedAndFallbackIsEnglish() throws Exception {
        Files.createDirectories(root.resolve("lang"));
        String original = "# Keep\nchecked: 'Grüße {file}: {total}'\n";
        Path own = root.resolve("lang/own.yml");
        Files.writeString(own, original);
        var catalog = MessageCatalog.load(root, "own");
        assertEquals("Grüße city.cbl: 3", catalog.text("checked", Map.of("file", "city.cbl", "total", 3)));
        assertEquals("No batch has run in this server session.", catalog.text("idle", Map.of()));
        MessageCatalog.load(root, "own");
        assertEquals(original, Files.readString(own));
    }
    @Test void selectedFallbackDoesNotRewriteOrReprocessInsertedText() throws Exception {
        Files.createDirectories(root.resolve("lang"));
        String original = "checked: '{file} {total}'\n";
        Files.writeString(root.resolve("lang/de.yml"), original);
        var catalog = MessageCatalog.load(root, "de");
        assertEquals("{total} $1 \\quote 7", catalog.text("checked", Map.of("file", "{total} $1 \\quote", "total", 7)));
        assertEquals("In dieser Serversitzung wurde noch kein Stapel ausgeführt.", catalog.text("idle", Map.of()));
        assertEquals(original, Files.readString(root.resolve("lang/de.yml")));
    }
    @ParameterizedTest @ValueSource(strings = {"idle: 4\n", "idle: |\n  first\n  second\n", "idle: [bad]\n", "idle: [\n"})
    void invalidActiveLanguageIsPreserved(String invalid) throws Exception {
        Files.createDirectories(root.resolve("lang"));
        Path path = root.resolve("lang/de.yml");
        Files.writeString(path, invalid);
        assertThrows(InvalidConfigurationException.class, () -> MessageCatalog.load(root, "de"));
        assertEquals(invalid, Files.readString(path));
    }
}
