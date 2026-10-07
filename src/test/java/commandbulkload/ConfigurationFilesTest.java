// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: config preservation, migration backups and failure handling.
package commandbulkload;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationFilesTest {
    @TempDir Path root;
    private java.io.InputStream defaults() { return getClass().getResourceAsStream("/config.yml"); }
    @Test void freshInstallAndRepeatedLoadPreserveConfiguration() throws Exception {
        var settings = ConfigurationFiles.load(root, defaults());
        assertEquals(20, settings.intervalTicks());
        assertEquals("en", settings.language());
        String original = Files.readString(root.resolve("config.yml"));
        ConfigurationFiles.load(root, defaults());
        assertEquals(original, Files.readString(root.resolve("config.yml")));
        assertFalse(Files.exists(root.resolve("config.yml.bak")));
    }
    @ParameterizedTest @ValueSource(strings = {"interval-ticks: 0", "max-file-bytes: -1", "max-commands: '5'", "progress-every: 1.5", "language: unknown", "config-version: 1", "config-version: broken"})
    void invalidDataNeverRewritesExistingFile(String bad) throws Exception {
        Path file = root.resolve("config.yml");
        Files.writeString(file, bad + "\n");
        assertThrows(InvalidConfigurationException.class, () -> ConfigurationFiles.load(root, defaults()));
        assertEquals(bad + "\n", Files.readString(file));
        assertFalse(Files.exists(root.resolve("config.yml.bak")));
    }
    @Test void versionOnlyUpdateRetainsCommentsCustomValuesAndMakesNoBackup() throws Exception {
        String original = "# Keep\ninterval-ticks: 7\nlanguage: en\n";
        Files.writeString(root.resolve("config.yml"), original);
        var settings = ConfigurationFiles.load(root, defaults());
        assertEquals(7, settings.intervalTicks());
        assertTrue(Files.readString(root.resolve("config.yml")).endsWith(original));
        assertFalse(Files.exists(root.resolve("config.yml.bak")));
    }
    @Test void preservesNewerConfigMarkerWithoutDowngrade() throws Exception {
        String original = "config-version: '2.0.0'\ninterval-ticks: 3\n";
        Files.writeString(root.resolve("config.yml"), original);
        assertEquals(3, ConfigurationFiles.load(root, defaults()).intervalTicks());
        assertEquals(original, Files.readString(root.resolve("config.yml")));
    }
    @Test void realFutureMigrationsRunOnceInOrderWithExactNumberedBackups() throws Exception {
        Path file = root.resolve("config.yml");
        String original = "# Keep\nconfig-version: '1.0.0-BETA.1'\ninterval-ticks: 4\n";
        Files.writeString(file, original);
        Files.writeString(root.resolve("config.yml.bak"), "previous backup");
        var steps = List.of(new ConfigurationFiles.Migration("1.0.0-BETA.2", yaml -> yaml.set("interval-ticks", 8)),
                new ConfigurationFiles.Migration("1.0.0-BETA.3", yaml -> yaml.set("interval-ticks", yaml.getInt("interval-ticks") + 1)));
        String resource = new String(defaults().readAllBytes(), StandardCharsets.UTF_8).replace("1.0.0", "1.0.0-BETA.3");
        var settings = ConfigurationFiles.load(root, new ByteArrayInputStream(resource.getBytes(StandardCharsets.UTF_8)), steps);
        assertEquals(9, settings.intervalTicks());
        assertEquals(original, Files.readString(root.resolve("config.yml.bak.1")));
        assertTrue(Files.readString(root.resolve("config.yml.bak.2")).contains("interval-ticks: 8"));
        assertEquals("previous backup", Files.readString(root.resolve("config.yml.bak")));
        String migrated = Files.readString(file);
        ConfigurationFiles.load(root, new ByteArrayInputStream(resource.getBytes(StandardCharsets.UTF_8)), steps);
        assertEquals(migrated, Files.readString(file));
        assertFalse(Files.exists(root.resolve("config.yml.bak.3")));
    }
    @Test void failedMigrationRetainsTheLastCompletedStep() throws Exception {
        Path file = root.resolve("config.yml");
        Files.writeString(file, "config-version: '1.0.0-BETA.1'\n");
        String resource = new String(defaults().readAllBytes(), StandardCharsets.UTF_8).replace("1.0.0", "1.0.0-BETA.3");
        var steps = List.of(new ConfigurationFiles.Migration("1.0.0-BETA.2", yaml -> yaml.set("interval-ticks", 6)),
                new ConfigurationFiles.Migration("1.0.0-BETA.3", yaml -> { throw new IllegalStateException("stop"); }));
        assertThrows(IllegalStateException.class, () -> ConfigurationFiles.load(root,
                new ByteArrayInputStream(resource.getBytes(StandardCharsets.UTF_8)), steps));
        String saved = Files.readString(file);
        assertTrue(saved.contains("config-version: 1.0.0-BETA.2"));
        assertTrue(saved.contains("interval-ticks: 6"));
        assertFalse(Files.exists(root.resolve("config.yml.bak.1")));
    }
    @Test void missingOwnLanguageDoesNotRewriteConfig() throws Exception {
        String original = "language: own\n";
        Files.writeString(root.resolve("config.yml"), original);
        assertThrows(java.io.IOException.class, () -> ConfigurationFiles.load(root, defaults()));
        assertEquals(original, Files.readString(root.resolve("config.yml")));
    }
    @ParameterizedTest @ValueSource(strings = {"config-version", "'config-version'", "\"config-version\""})
    void versionUpdatesPreserveQuotedKeysAndInlineComments(String key) throws Exception {
        String original = "# keep\n" + key + ": '1.0.0-BETA.0' # marker\ninterval-ticks: 7\n";
        Path file = root.resolve("config.yml");
        Files.writeString(file, original);
        ConfigurationFiles.load(root, defaults());
        assertEquals(original.replace("1.0.0-BETA.0", "1.0.0"), Files.readString(file));
        ConfigurationFiles.load(root, defaults());
        assertEquals(original.replace("1.0.0-BETA.0", "1.0.0"), Files.readString(file));
        assertFalse(Files.exists(root.resolve("config.yml.bak")));
    }

}
