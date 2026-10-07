// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: validated settings with independent future migration steps.
package commandbulkload;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class ConfigurationFiles {
    record Settings(long intervalTicks, long maxFileBytes, int maxCommands, int progressEvery, String language) { }
    record Migration(String target, Consumer<YamlConfiguration> apply) { }
    // Initial release has no legacy configuration. Add only real future migrations.
    private static final List<Migration> MIGRATIONS = List.of();

    static Settings load(Path directory, InputStream resource) throws IOException, InvalidConfigurationException {
        return load(directory, resource, MIGRATIONS);
    }
    static Settings load(Path directory, InputStream resource, List<Migration> migrations)
            throws IOException, InvalidConfigurationException {
        if (resource == null) throw new IOException("Bundled config.yml is missing.");
        var defaults = new YamlConfiguration();
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) { defaults.load(reader); }
        validate(defaults);
        String running = defaults.getString("config-version");
        var runningVersion = version(running);
        Path file = directory.resolve("config.yml");
        boolean exists = Files.exists(file);
        var config = new YamlConfiguration();
        if (exists) config.load(file.toFile());
        var stored = config.contains("config-version", true) ? version(config.getString("config-version")) : null;
        ReleaseVersion previous = null;
        for (var migration : migrations) {
            var target = version(migration.target());
            if (previous != null && previous.compareTo(target) >= 0) throw new IllegalStateException("Migration targets must increase.");
            if (target.compareTo(runningVersion) > 0) throw new IllegalStateException("Migration exceeds plugin version.");
            previous = target;
            if (exists && (stored == null || stored.compareTo(target) < 0)) {
                // Work on a copy so a failed step never changes the last saved state.
                var next = new YamlConfiguration();
                next.loadFromString(config.saveToString());
                for (var key : defaults.getKeys(false)) if (!next.contains(key, true)) next.set(key, defaults.get(key));
                migration.apply().accept(next);
                next.set("config-version", migration.target());
                validate(next);
                save(file, next.saveToString(), true);
                config = next;
                stored = target;
            }
        }
        var effective = new YamlConfiguration();
        effective.loadFromString(config.saveToString());
        for (var key : defaults.getKeys(false)) if (!effective.contains(key, true)) effective.set(key, defaults.get(key));
        Settings settings = validate(effective);
        // Validate language data before touching an existing configuration.
        MessageCatalog.load(directory, settings.language());
        if (!exists) save(file, defaults.saveToString(), false);
        else if (stored == null || stored.compareTo(runningVersion) < 0) {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            var marker = java.util.regex.Pattern.compile(
                    "(?m)^([ \t]*(?:config-version|\"config-version\"|'config-version')\\s*:[ \t]*)"
                    + "(?:'[^'\\r\\n]*'|\"[^\"\\r\\n]*\"|[^#\\r\\n]*?)([ \t]*(?:#[^\\r\\n]*)?)$").matcher(original);
            String replacement = "config-version: '" + running + "'";
            String updated = marker.find() ? marker.replaceFirst(match ->
                    java.util.regex.Matcher.quoteReplacement(match.group(1) + "'" + running + "'" + match.group(2)))
                    : replacement + "\n" + original;
            save(file, updated, false);
        }
        return settings;
    }
    private static ReleaseVersion version(String value) throws InvalidConfigurationException {
        try { return ReleaseVersion.parse(value); }
        catch (IllegalArgumentException failure) { throw new InvalidConfigurationException(failure.getMessage(), failure); }
    }
    static Settings validate(YamlConfiguration config) throws InvalidConfigurationException {
        long interval = number(config, "interval-ticks", 72000);
        long size = number(config, "max-file-bytes", 67108864);
        int count = (int) number(config, "max-commands", 1000000);
        int progress = (int) number(config, "progress-every", 1000000);
        String language = config.getString("language");
        if (!config.isString("language") || !(MessageCatalog.LANGUAGES.contains(language) || "own".equals(language))) {
            throw new InvalidConfigurationException("language must be en, de, es, fr, pt_br, pl, tr or own.");
        }
        if (config.contains("config-version")) {
            if (!config.isString("config-version")) throw new InvalidConfigurationException("config-version must be text.");
            version(config.getString("config-version"));
        }
        return new Settings(interval, size, count, progress, language);
    }
    private static long number(YamlConfiguration config, String key, long maximum) throws InvalidConfigurationException {
        if (!(config.get(key) instanceof Integer || config.get(key) instanceof Long)) {
            throw new InvalidConfigurationException(key + " must be an integer.");
        }
        long value = config.getLong(key);
        if (value < 1 || value > maximum) throw new InvalidConfigurationException(key + " must be between 1 and " + maximum + ".");
        return value;
    }
    static void save(Path path, String text, boolean backup) throws IOException {
        Files.createDirectories(path.getParent());
        if (backup && Files.exists(path)) {
            int index = 0;
            Path destination;
            do { destination = path.resolveSibling(path.getFileName() + ".bak" + (index == 0 ? "" : "." + index)); index++; }
            while (Files.exists(destination));
            Files.copy(path, destination);
        }
        Path temporary = Files.createTempFile(path.getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
