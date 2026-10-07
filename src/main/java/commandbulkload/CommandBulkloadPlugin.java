// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: authorized batch commands and main-thread coordination.
package commandbulkload;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.nio.file.Files;

public final class CommandBulkloadPlugin extends JavaPlugin implements BatchRunner.Observer {
    private ConfigurationFiles.Settings settings;
    private MessageCatalog messages;
    private BatchRunner runner;
    private RunJournal journal;
    private Path uploadsDirectory, logDirectory, dataDirectory;
    private CommandSender recipient;
    private volatile boolean stopped;
    private String loadingFile;
    private long loadGeneration;

    @Override public void onEnable() {
        stopped = false;
        try {
            dataDirectory = getDataFolder().toPath();
            uploadsDirectory = Files.createDirectories(dataDirectory.resolve("Uploads")).toRealPath();
            logDirectory = Files.createDirectories(Path.of("").toRealPath().resolve("logs/CommandBulkload"));
            settings = ConfigurationFiles.load(dataDirectory, getResource("config.yml"));
            messages = MessageCatalog.load(dataDirectory, settings.language());
            runner = new BatchRunner((task, ticks) -> {
                var scheduled = getServer().getScheduler().runTaskLater(this, task, ticks);
                return scheduled::cancel;
            }, command -> getServer().dispatchCommand(getServer().getConsoleSender(), command),
                    this, settings.intervalTicks(), settings.progressEvery());
            getServer().getConsoleSender().sendMessage(net.kyori.adventure.text.Component.text(
                    "[CommandBulkload] CommandBulkload enabled!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            say("enabled", Map.of("root", uploadsDirectory));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException failure) {
            getLogger().log(Level.SEVERE, "CommandBulkload startup failed. No batch can run. Fix configuration and restart.", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!authorized(sender)) {
            sender.sendMessage(messages == null ? "[CommandBulkload] You do not have permission to use this command."
                    : messages.prefix() + messages.text("no-permission", Map.of()));
            return true;
        }
        if (stopped || runner == null) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (loadingFile != null) reply(sender, "loading", Map.of("file", loadingFile));
            else if (runner.snapshot().state() == BatchRunner.State.IDLE) reply(sender, "idle", Map.of());
            else reply(sender, "status", details(runner.snapshot()));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (loadingFile != null) {
                loadingFile = null;
                loadGeneration++;
                reply(sender, "cancel-loading", Map.of());
            } else if (!runner.cancel("Cancelled by an authorized sender.")) reply(sender, "no-run", Map.of());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("run") || args[0].equalsIgnoreCase("check"))) {
            if (loadingFile != null || runner.active()) reply(sender, "busy", Map.of());
            else load(args[1], args[0].equalsIgnoreCase("run"), sender);
        } else reply(sender, "usage", Map.of());
        return true;
    }
    private void load(String filename, boolean execute, CommandSender sender) {
        recipient = sender;
        loadingFile = filename;
        long token = ++loadGeneration;
        say("loading", Map.of("file", filename));
        if (sender instanceof RemoteConsoleCommandSender) reply(sender, "loading", Map.of("file", filename));
        try {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                BatchParser.Batch batch = null;
                RunJournal audit = null;
                String error = null;
                try {
                    batch = new BatchParser().read(uploadsDirectory, filename, settings.maxFileBytes(), settings.maxCommands());
                    if (execute) audit = RunJournal.open(logDirectory, batch);
                } catch (IOException | RuntimeException failure) {
                    error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                }
                final var parsed = batch;
                final var log = audit;
                final var problem = error;
                if (stopped) { closeUnused(log); return; }
                try {
                    getServer().getScheduler().runTask(this, () -> loaded(token, filename, execute, parsed, log, problem));
                } catch (RuntimeException disabled) { closeUnused(log); }
            });
        } catch (RuntimeException failure) {
            loadingFile = null;
            say("read-error", Map.of("file", filename, "reason", String.valueOf(failure.getMessage())));
        }
    }
    private void loaded(long token, String filename, boolean execute, BatchParser.Batch batch, RunJournal audit, String error) {
        if (stopped || loadGeneration != token) { closeUnused(audit); return; }
        loadingFile = null;
        if (!authorized(recipient) || recipient instanceof Player player && !player.isOnline()) {
            closeUnused(audit);
            say("no-permission", Map.of());
            return;
        }
        if (error != null) { closeUnused(audit); say("read-error", Map.of("file", filename, "reason", error)); return; }
        if (!execute) {
            say("checked", Map.of("file", filename, "total", batch.commands().size()));
            return;
        }
        journal = audit;
        runner.start(batch);
    }
    @Override public void started(BatchParser.Batch batch) {
        journal.write("START");
        say("started", Map.of("file", batch.filename(), "total", batch.commands().size(), "log", journal.file()));
    }
    @Override public void attempted(BatchParser.CommandLine command, boolean accepted, String error) {
        journal.write("LINE " + command.line() + " DISPATCH " + (accepted ? "ACCEPTED" : "FAILED") + " COMMAND " + command.command()
                + (error.isEmpty() ? "" : " REASON " + error));
    }
    @Override public void progress(BatchRunner.Snapshot snapshot) { say("progress", details(snapshot)); }
    @Override public void finished(BatchRunner.Snapshot snapshot, String reason) {
        var fields = new java.util.LinkedHashMap<String, Object>(details(snapshot));
        fields.put("reason", reason);
        try { if (journal != null) journal.write("END " + snapshot + " REASON " + reason); }
        catch (RuntimeException failure) { getLogger().log(Level.SEVERE, "Could not finish audit log.", failure); }
        finally { closeUnused(journal); journal = null; }
        say(snapshot.state() == BatchRunner.State.COMPLETED ? "finished" : "stopped", fields);
    }
    private Map<String, Object> details(BatchRunner.Snapshot snapshot) {
        return Map.of("state", messages.text("state." + snapshot.state().name().toLowerCase(java.util.Locale.ROOT), Map.of()),
                "file", snapshot.file(), "total", snapshot.total(), "attempted", snapshot.attempted(),
                "dispatched", snapshot.dispatched(), "errors", snapshot.errors(), "remaining", snapshot.remaining(), "line", snapshot.lastLine());
    }
    private static boolean authorized(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender
                || sender instanceof Player player && (player.isOp() || player.hasPermission("commandbulkload.command"));
    }
    private void reply(CommandSender sender, String key, Map<String, ?> fields) {
        sender.sendMessage(messages.prefix() + messages.text(key, fields));
    }
    private void say(String key, Map<String, ?> fields) {
        getServer().getConsoleSender().sendMessage(messages.prefix() + messages.text(key, fields));
        if (recipient instanceof Player player && player.isOnline() && authorized(player)) reply(player, key, fields);
    }
    private void closeUnused(RunJournal audit) {
        if (audit == null) return;
        try { audit.close(); }
        catch (IOException failure) { getLogger().log(Level.SEVERE, "Could not close audit log.", failure); }
    }
    @Override public void onDisable() {
        stopped = true;
        loadingFile = null;
        loadGeneration++;
        if (runner != null) runner.cancel("Plugin disabled; pending commands were cancelled.");
        // A dispatch-triggered shutdown is finalized by the runner after dispatch returns.
        if (runner == null || !runner.dispatching()) { closeUnused(journal); journal = null; }
        getServer().getScheduler().cancelTasks(this);
    }
}
