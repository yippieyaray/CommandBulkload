// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: editable single-line languages with English and own fallback.
package commandbulkload;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class MessageCatalog {
    static final List<String> LANGUAGES = List.of("en", "de", "es", "fr", "pt_br", "pl", "tr");
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z-]+)\\}");
    private static final String[][] LEGACY_LINES = {
            new String[] {"en", "progress", "progress: '{file}: {attempted}/{total} attempted, {dispatched} dispatched.'", "progress: '{file}: {dispatched} of {total} dispatched.'"},
            new String[] {"en", "finished", "finished: 'Finished {file}: {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining. Target-plugin completion is not confirmed.'", "finished: 'Finished {file}: {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining.'"},
            new String[] {"de", "progress", "progress: '{file}: {attempted}/{total} versucht, {dispatched} abgesendet.'", "progress: '{file}: {dispatched} von {total} abgesendet.'"},
            new String[] {"de", "finished", "finished: '{file} abgeschlossen: {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend. Verarbeitung durch Zielplugins ist nicht bestätigt.'", "finished: '{file} abgeschlossen: {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend.'"},
            new String[] {"es", "progress", "progress: '{file}: {attempted}/{total} intentados, {dispatched} enviados.'", "progress: '{file}: {dispatched} de {total} enviados.'"},
            new String[] {"es", "finished", "finished: '{file} terminado: {dispatched} enviados, {errors} errores de envío, {remaining} pendientes. El procesamiento por otros plugins no está confirmado.'", "finished: '{file} terminado: {dispatched} enviados, {errors} errores de envío, {remaining} pendientes.'"},
            new String[] {"fr", "progress", "progress: '{file} : {attempted}/{total} tentées, {dispatched} envoyées.'", "progress: '{file} : {dispatched} sur {total} envoyées.'"},
            new String[] {"fr", "finished", "finished: '{file} terminé : {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes. Le traitement par les plugins destinataires n’est pas confirmé.'", "finished: '{file} terminé : {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes.'"},
            new String[] {"pt_br", "progress", "progress: '{file}: {attempted}/{total} tentados, {dispatched} enviados.'", "progress: '{file}: {dispatched} de {total} enviados.'"},
            new String[] {"pt_br", "finished", "finished: '{file} concluído: {dispatched} enviados, {errors} erros de envio, {remaining} restantes. O processamento pelos plugins de destino não foi confirmado.'", "finished: '{file} concluído: {dispatched} enviados, {errors} erros de envio, {remaining} restantes.'"},
            new String[] {"pl", "progress", "progress: '{file}: próby {attempted}/{total}, wysłano {dispatched}.'", "progress: '{file}: wysłano {dispatched} z {total}.'"},
            new String[] {"pl", "finished", "finished: 'Zakończono {file}: wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}. Przetworzenie przez docelowe wtyczki nie jest potwierdzone.'", "finished: 'Zakończono {file}: wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}.'"},
            new String[] {"tr", "progress", "progress: '{file}: {attempted}/{total} denendi, {dispatched} gönderildi.'", "progress: '{file}: {total} komuttan {dispatched} gönderildi.'"},
            new String[] {"tr", "finished", "finished: '{file} tamamlandı: {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan. Hedef eklentilerin işlemesi doğrulanmadı.'", "finished: '{file} tamamlandı: {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan.'"},
            new String[] {"es", "started", "started: '{file} iniciado: {total} comandos. Registro: {log}'", "started: '{file} iniciado: {total} comandos.'"},
            new String[] {"es", "progress", "progress: '{file}: {dispatched} de {total} enviados.'", "progress: '{dispatched} de {total} enviados.'"},
            new String[] {"es", "finished", "finished: '{file} terminado: {dispatched} enviados, {errors} errores de envío, {remaining} pendientes.'", "finished: 'terminado: {dispatched} enviados, {errors} errores de envío, {remaining} pendientes.'"},
            new String[] {"es", "stopped", "stopped: '{state}: {file}, línea {line}; {dispatched} enviados, {errors} errores de envío, {remaining} pendientes. {reason}'", "stopped: '{state}: línea {line}; {dispatched} enviados, {errors} errores de envío, {remaining} pendientes. {reason}'"},
            new String[] {"de", "started", "started: '{file} gestartet: {total} Kommandos. Laufprotokoll: {log}'", "started: '{file} gestartet: {total} Kommandos.'"},
            new String[] {"de", "progress", "progress: '{file}: {dispatched} von {total} abgesendet.'", "progress: '{dispatched} von {total} abgesendet.'"},
            new String[] {"de", "finished", "finished: '{file} abgeschlossen: {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend.'", "finished: 'abgeschlossen: {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend.'"},
            new String[] {"de", "stopped", "stopped: '{state}: {file}, Zeile {line}; {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend. {reason}'", "stopped: '{state}: Zeile {line}; {dispatched} abgesendet, {errors} Dispatch-Fehler, {remaining} verbleibend. {reason}'"},
            new String[] {"pl", "started", "started: 'Uruchomiono {file}: {total} poleceń. Dziennik: {log}'", "started: 'Uruchomiono {file}: {total} poleceń.'"},
            new String[] {"pl", "progress", "progress: '{file}: wysłano {dispatched} z {total}.'", "progress: 'wysłano {dispatched} z {total}.'"},
            new String[] {"pl", "finished", "finished: 'Zakończono {file}: wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}.'", "finished: 'Zakończono: wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}.'"},
            new String[] {"pl", "stopped", "stopped: '{state}: {file}, wiersz {line}; wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}. {reason}'", "stopped: '{state}: wiersz {line}; wysłano {dispatched}, błędy wysyłania {errors}, pozostało {remaining}. {reason}'"},
            new String[] {"en", "started", "started: 'Started {file}: {total} commands. Audit log: {log}'", "started: 'Started {file}: {total} commands.'"},
            new String[] {"en", "progress", "progress: '{file}: {dispatched} of {total} dispatched.'", "progress: '{dispatched} of {total} dispatched.'"},
            new String[] {"en", "finished", "finished: 'Finished {file}: {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining.'", "finished: 'Finished: {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining.'"},
            new String[] {"en", "stopped", "stopped: '{state}: {file}, line {line}; {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining. {reason}'", "stopped: '{state}: line {line}; {dispatched} dispatched, {errors} dispatch errors, {remaining} remaining. {reason}'"},
            new String[] {"pt_br", "started", "started: '{file} iniciado: {total} comandos. Registro: {log}'", "started: '{file} iniciado: {total} comandos.'"},
            new String[] {"pt_br", "progress", "progress: '{file}: {dispatched} de {total} enviados.'", "progress: '{dispatched} de {total} enviados.'"},
            new String[] {"pt_br", "finished", "finished: '{file} concluído: {dispatched} enviados, {errors} erros de envio, {remaining} restantes.'", "finished: 'concluído: {dispatched} enviados, {errors} erros de envio, {remaining} restantes.'"},
            new String[] {"pt_br", "stopped", "stopped: '{state}: {file}, linha {line}; {dispatched} enviados, {errors} erros de envio, {remaining} restantes. {reason}'", "stopped: '{state}: linha {line}; {dispatched} enviados, {errors} erros de envio, {remaining} restantes. {reason}'"},
            new String[] {"fr", "started", "started: '{file} démarré : {total} commandes. Journal : {log}'", "started: '{file} démarré : {total} commandes.'"},
            new String[] {"fr", "progress", "progress: '{file} : {dispatched} sur {total} envoyées.'", "progress: '{dispatched} sur {total} envoyées.'"},
            new String[] {"fr", "finished", "finished: '{file} terminé : {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes.'", "finished: 'terminé : {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes.'"},
            new String[] {"fr", "stopped", "stopped: '{state} : {file}, ligne {line} ; {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes. {reason}'", "stopped: '{state} : ligne {line} ; {dispatched} envoyées, {errors} erreurs d’envoi, {remaining} restantes. {reason}'"},
            new String[] {"tr", "started", "started: '{file} başlatıldı: {total} komut. Günlük: {log}'", "started: '{file} başlatıldı: {total} komut.'"},
            new String[] {"tr", "progress", "progress: '{file}: {total} komuttan {dispatched} gönderildi.'", "progress: '{total} komuttan {dispatched} gönderildi.'"},
            new String[] {"tr", "finished", "finished: '{file} tamamlandı: {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan.'", "finished: 'tamamlandı: {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan.'"},
            new String[] {"tr", "stopped", "stopped: '{state}: {file}, satır {line}; {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan. {reason}'", "stopped: '{state}: satır {line}; {dispatched} gönderildi, {errors} gönderim hatası, {remaining} kalan. {reason}'"},
            new String[] {"es", "loading", "loading: 'Leyendo {file}...'", "loading: 'Leyendo...'"},
            new String[] {"de", "loading", "loading: 'Lese {file}...'", "loading: 'Lese...'"},
            new String[] {"pl", "loading", "loading: 'Odczytywanie {file}...'", "loading: 'Odczytywanie...'"},
            new String[] {"en", "loading", "loading: 'Reading {file}...'", "loading: 'Reading...'"},
            new String[] {"pt_br", "loading", "loading: 'Lendo {file}...'", "loading: 'Lendo...'"},
            new String[] {"fr", "loading", "loading: 'Lecture de {file}...'", "loading: 'Lecture de...'"},
            new String[] {"tr", "loading", "loading: '{file} okunuyor...'", "loading: '{file} okunuyor...'"},
            new String[] {"tr", "loading", "loading: '{file} okunuyor...'", "loading: 'okunuyor...'"}
    };
    private final Map<String, String> texts;
    private MessageCatalog(Map<String, String> texts) { this.texts = Map.copyOf(texts); }
    static MessageCatalog load(Path directory, String language) throws IOException, InvalidConfigurationException {
        Map<String, String> effective = new LinkedHashMap<>();
        overlay(effective, bundled("en"));
        if (!"own".equals(language)) overlay(effective, bundled(language));
        Path folder = Files.createDirectories(directory.resolve("lang"));
        for (String code : LANGUAGES) {
            Path file = folder.resolve(code + ".yml");
            if (!Files.exists(file)) {
                try (var stream = MessageCatalog.class.getResourceAsStream("/lang/" + code + ".yml")) {
                    if (stream == null) throw new IOException("Missing bundled language: " + code);
                    Files.copy(stream, file);
                }
            }
        }
        migrateStandardMessages(folder);
        var custom = new YamlConfiguration();
        custom.options().pathSeparator('/');
        custom.load(folder.resolve(language + ".yml").toFile());
        overlay(effective, custom);
        return new MessageCatalog(effective);
    }
    // Replace only exact bundled legacy lines; preserve custom text and formatting.
    private static void migrateStandardMessages(Path folder) throws IOException {
        for (String code : LANGUAGES) {
            Path file = folder.resolve(code + ".yml");
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String updated = original;
            for (String[] entry : LEGACY_LINES) {
                if (entry[0].equals(code)) {
                    updated = Pattern.compile("(?m)^" + Pattern.quote(entry[2]) + "$")
                            .matcher(updated).replaceAll(Matcher.quoteReplacement(entry[3]));
                }
            }
            if (!original.equals(updated)) ConfigurationFiles.save(file, updated, true);
        }
    }
    static YamlConfiguration bundled(String language) throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        var stream = MessageCatalog.class.getResourceAsStream("/lang/" + language + ".yml");
        if (stream == null) throw new IOException("Missing bundled language: " + language);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { yaml.load(reader); }
        return yaml;
    }
    private static void overlay(Map<String, String> target, YamlConfiguration source) throws InvalidConfigurationException {
        for (String key : source.getKeys(false)) {
            if (!source.isString(key) || Pattern.compile("\\R").matcher(source.getString(key)).find()) {
                throw new InvalidConfigurationException("Language entry must be single-line text: " + key);
            }
            target.put(key, source.getString(key));
        }
    }
    String text(String key, Map<String, ?> values) {
        String template = texts.get(key);
        if (template == null) throw new IllegalArgumentException("Unknown message: " + key);
        Matcher matcher = TOKEN.matcher(template);
        String result = matcher.replaceAll(match -> Matcher.quoteReplacement(
                values.containsKey(match.group(1)) ? String.valueOf(values.get(match.group(1))) : match.group()));
        // Single-pass substitution leaves inserted text literal.
        return result;
    }
    String clientText(String key, Map<String, ?> values) {
        String color = switch (key) {
            case "no-permission", "read-error", "stopped" -> "§c";
            case "busy", "usage", "cancel-loading", "no-run" -> "§e";
            case "checked", "finished" -> "§a";
            default -> "§7";
        };
        String template = texts.get(key);
        if (template == null) throw new IllegalArgumentException("Unknown message: " + key);
        String body = TOKEN.matcher(template).replaceAll(match -> Matcher.quoteReplacement(
                values.containsKey(match.group(1))
                        ? "§b" + values.get(match.group(1)) + color : match.group()));
        String heading = "[CommandBulkload] ".equals(prefix())
                ? "§f[§2CommandBulkload§f] " : "§2" + prefix() + "§r ";
        return heading + color + body + "§r";
    }
    String prefix() { return texts.get("prefix"); }
}
