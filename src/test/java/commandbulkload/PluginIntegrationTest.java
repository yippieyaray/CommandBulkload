// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: console boundaries, loading races and scheduled integration.
package commandbulkload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginIntegrationTest {
    @TempDir Path root;
    private CommandBulkloadPlugin plugin;
    private Server server;
    private ConsoleCommandSender console;
    private BukkitScheduler scheduler;
    private final List<Runnable> background = new ArrayList<>(), main = new ArrayList<>(), delayed = new ArrayList<>();
    private final Command command = mock(Command.class);
    @BeforeEach void setup() throws Exception {
        plugin = mock(CommandBulkloadPlugin.class, CALLS_REAL_METHODS);
        server = mock(Server.class);
        console = mock(ConsoleCommandSender.class);
        scheduler = mock(BukkitScheduler.class);
        doReturn(root.resolve("plugin").toFile()).when(plugin).getDataFolder();
        doReturn(server).when(plugin).getServer();
        doReturn(Logger.getLogger("CommandBulkloadTest")).when(plugin).getLogger();
        doReturn(getClass().getResourceAsStream("/config.yml")).when(plugin).getResource("config.yml");
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> { background.add(call.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { main.add(call.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> { delayed.add(call.getArgument(1)); return mock(BukkitTask.class); });
        when(server.dispatchCommand(eq(console), anyString())).thenReturn(true);
        Files.createDirectories(root.resolve("plugin"));
        Files.writeString(root.resolve("plugin/config.yml"), "language: de\n");
        plugin.onEnable();
        Files.writeString(root.resolve("plugin/Uploads/batch.cbl"), "say one\nsay two\n");
        clearInvocations(console);
    }
    private void send(String... args) { plugin.onCommand(console, command, "commandbulkload", args); }
    private void loaded() { background.removeFirst().run(); main.removeFirst().run(); }
    @Test void unauthorizedPlayersAreRejectedBeforeAnyFileRead() {
        var player = mock(Player.class);
        plugin.onCommand(player, command, "commandbulkload", new String[] {"run", "batch.cbl"});
        verify(player).sendMessage(argThat((String message) -> message.replaceAll("§.", "").contains("keine Berechtigung")));
        assertTrue(background.isEmpty());
        verify(server, never()).dispatchCommand(any(), anyString());
    }
    @Test void checkIsNonExecutingAndBusyIncludesLoading() {
        send("check", "batch.cbl");
        send("run", "batch.cbl");
        assertEquals(1, background.size());
        verify(console).sendMessage(contains("Vor einem neuen Start abbrechen"));
        loaded();
        verify(console).sendMessage(contains("2 Kommandos; nichts abgesendet"));
        verify(server, never()).dispatchCommand(any(), anyString());
        assertTrue(delayed.isEmpty());
    }
    @Test void runLogsEachCommandBeforeDispatchWithoutCreatingSeparateFiles() throws Exception {
        send("run", "batch.cbl"); loaded();
        verify(server, never()).dispatchCommand(any(), anyString());
        delayed.removeFirst().run();
        verify(server).dispatchCommand(console, "say one");
        send("status");
        verify(console).sendMessage(contains("versucht 1/2"));
        delayed.removeFirst().run();
        verify(server).dispatchCommand(console, "say two");
        var order = inOrder(console, server);
        order.verify(console).sendMessage(argThat((net.kyori.adventure.text.Component message) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message)
                        .equals("[CommandBulkload] Dispatching line 1: say one")));
        order.verify(server).dispatchCommand(console, "say one");
        order.verify(console).sendMessage(argThat((net.kyori.adventure.text.Component message) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message)
                        .equals("[CommandBulkload] Dispatching line 2: say two")));
        order.verify(server).dispatchCommand(console, "say two");
        verify(console).sendMessage(argThat((net.kyori.adventure.text.Component message) ->
                message.children().stream().anyMatch(part -> part instanceof net.kyori.adventure.text.TextComponent text
                        && text.content().equals("say one")
                        && net.kyori.adventure.text.format.NamedTextColor.WHITE.equals(text.color()))));
        assertFalse(Files.exists(root.resolve("logs/CommandBulkload")));
        verify(console).sendMessage(contains("abgeschlossen: 2 abgesendet, 0 Dispatch-Fehler, 0 verbleibend."));
    }
    @Test void cancelledLoadingCannotStartAfterCallbackAndNewLoadsStayIndependent() {
        send("run", "batch.cbl");
        send("cancel");
        send("check", "batch.cbl");
        loaded();
        assertTrue(delayed.isEmpty());
        loaded();
        verify(server, never()).dispatchCommand(any(), anyString());
    }
    @Test void cancelAndDisableBlockAlreadyQueuedCallbacks() {
        send("run", "batch.cbl"); loaded();
        send("cancel"); delayed.removeFirst().run();
        verify(server, never()).dispatchCommand(any(), anyString());
        send("run", "batch.cbl"); loaded();
        plugin.onDisable(); delayed.removeFirst().run();
        verify(server, never()).dispatchCommand(any(), anyString());
        verify(scheduler).cancelTasks(plugin);
    }
    @Test void dispatchFailureStopsAndRecordsOriginalLine() throws Exception {
        when(server.dispatchCommand(console, "say one")).thenReturn(false);
        send("run", "batch.cbl"); loaded(); delayed.removeFirst().run();
        assertTrue(delayed.isEmpty());
        verify(server, never()).dispatchCommand(console, "say two");
        verify(console).sendMessage(contains("Zeile 1"));
    }
    @Test void commandTriggeredDisableReportsCurrentDispatchAndStopsTheNext() throws Exception {
        when(server.dispatchCommand(console, "say one")).thenAnswer(call -> { plugin.onDisable(); return true; });
        send("run", "batch.cbl"); loaded(); delayed.removeFirst().run();
        assertTrue(delayed.isEmpty());
        verify(server, never()).dispatchCommand(console, "say two");
        verify(console).sendMessage(contains("1 abgesendet"));
    }
    @Test void malformedFileRunsNoPartialCommands() throws Exception {
        Files.writeString(root.resolve("plugin/Uploads/batch.cbl"), "say first\n/\n");
        send("run", "batch.cbl"); loaded();
        assertTrue(delayed.isEmpty());
        verify(server, never()).dispatchCommand(any(), anyString());
        verify(console).sendMessage(contains("line 2"));
    }
    @Test void remoteConsoleCanStartAndReceiveImmediateStatus() {
        var remote = mock(org.bukkit.command.RemoteConsoleCommandSender.class);
        plugin.onCommand(remote, command, "commandbulkload", new String[] {"run", "batch.cbl"});
        assertEquals(1, background.size());
        verify(remote).sendMessage(contains("Lese..."));
        plugin.onCommand(remote, command, "commandbulkload", new String[] {"status"});
        verify(remote, times(2)).sendMessage(contains("Lese..."));
        plugin.onCommand(remote, command, "commandbulkload", new String[] {"cancel"});
        loaded();
        assertTrue(delayed.isEmpty());
    }
    @Test void commandBlocksCannotRunBatches() {
        var block = mock(org.bukkit.command.BlockCommandSender.class);
        plugin.onCommand(block, command, "commandbulkload", new String[] {"run", "batch.cbl"});
        assertTrue(background.isEmpty());
        verify(block).sendMessage(contains("keine Berechtigung"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void operatorOrLuckPermsGrantCanRunAnyServerCommand(boolean op) throws Exception {
        var player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.isOp()).thenReturn(op);
        when(player.hasPermission("commandbulkload.command")).thenReturn(!op);
        Files.writeString(root.resolve("plugin/Uploads/batch.cbl"), "say hi\nversion\n");
        plugin.onCommand(player, command, "commandbulkload", new String[] {"run", "batch.cbl"});
        verify(player).sendMessage(argThat((String message) -> message.replaceAll("§.", "").contains("Lese...")));
        loaded();
        verify(player).sendMessage(argThat((String message) -> message.replaceAll("§.", "").contains("gestartet")));
        delayed.removeFirst().run(); delayed.removeFirst().run();
        verify(server).dispatchCommand(console, "say hi");
        verify(server).dispatchCommand(console, "version");
        verify(server, never()).dispatchCommand(eq(player), anyString());
        verify(player).sendMessage(argThat((String message) -> message.replaceAll("§.", "").contains("abgeschlossen")));
    }
    @Test void revokedPermissionDuringLoadingPreventsDispatch() {
        var player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("commandbulkload.command")).thenReturn(true);
        plugin.onCommand(player, command, "commandbulkload", new String[] {"run", "batch.cbl"});
        when(player.hasPermission("commandbulkload.command")).thenReturn(false);
        loaded();
        assertTrue(delayed.isEmpty());
        verify(server, never()).dispatchCommand(any(), anyString());
    }
    @Test void unauthorizedPlayersCannotInspectOrCancelAnExistingRun() {
        send("run", "batch.cbl"); loaded();
        var player = mock(Player.class);
        plugin.onCommand(player, command, "commandbulkload", new String[] {"status"});
        plugin.onCommand(player, command, "commandbulkload", new String[] {"cancel"});
        verify(player, times(2)).sendMessage(argThat((String message) -> message.replaceAll("§.", "").contains("keine Berechtigung")));
        delayed.removeFirst().run();
        verify(server).dispatchCommand(console, "say one");
    }
    @Test void serverRootFileIsNotReadWhenUploadFileIsMissing() throws Exception {
        Files.delete(root.resolve("plugin/Uploads/batch.cbl"));
        Files.writeString(root.resolve("batch.cbl"), "say root file\n");
        send("run", "batch.cbl"); loaded();
        verify(server, never()).dispatchCommand(any(), anyString());
        assertTrue(delayed.isEmpty());
    }

}
