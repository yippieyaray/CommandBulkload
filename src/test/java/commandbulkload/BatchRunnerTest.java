// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: scheduled execution and cancellation regression tests.
package commandbulkload;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchRunnerTest {
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private final BatchRunner.Observer observer = mock(BatchRunner.Observer.class);
    private final BatchParser.Batch batch = new BatchParser.Batch("batch.cbl", "hash", List.of(
            new BatchParser.CommandLine(3, "say a"), new BatchParser.CommandLine(5, "say a"), new BatchParser.CommandLine(8, "say b")));
    private BatchRunner runner(BatchRunner.Dispatcher dispatcher) {
        return new BatchRunner((task, delay) -> { tasks.add(task); delays.add(delay); return () -> {}; }, dispatcher, observer, 20, 1);
    }
    private void next() { tasks.removeFirst().run(); }
    @Test void dispatchesOnePerStepInOrderIncludingDuplicates() {
        var runner = runner(command -> { sent.add(command); return true; });
        runner.start(batch);
        assertTrue(sent.isEmpty());
        next(); assertEquals(List.of("say a"), sent);
        next(); next();
        assertEquals(List.of("say a", "say a", "say b"), sent);
        assertEquals(List.of(1L, 20L, 20L), delays);
        assertEquals(BatchRunner.State.COMPLETED, runner.snapshot().state());
        assertEquals(3, runner.snapshot().dispatched());
        assertEquals(0, runner.snapshot().remaining());
        verify(observer, times(2)).progress(any());
        verify(observer).finished(runner.snapshot(), "");
    }
    @Test void falseDispatchStopsAtOriginalLine() {
        var runner = runner(command -> false);
        runner.start(batch); next();
        assertEquals(BatchRunner.State.FAILED, runner.snapshot().state());
        assertEquals(3, runner.snapshot().lastLine());
        assertEquals(1, runner.snapshot().errors());
        assertEquals(2, runner.snapshot().remaining());
        assertTrue(tasks.isEmpty());
    }
    @Test void exceptionStopsFurtherCommands() {
        var runner = runner(command -> { throw new IllegalStateException("test failure"); });
        runner.start(batch); next();
        assertEquals(1, runner.snapshot().attempted());
        assertEquals(BatchRunner.State.FAILED, runner.snapshot().state());
        verify(observer).attempted(batch.commands().getFirst(), false, "IllegalStateException: test failure");
    }
    @Test void cancelledCallbacksNeverDispatchAndOldTokensCannotAffectNewRuns() {
        var runner = runner(command -> { sent.add(command); return true; });
        runner.start(batch);
        Runnable old = tasks.removeFirst();
        assertTrue(runner.cancel("cancelled"));
        runner.start(batch);
        old.run(); assertTrue(sent.isEmpty());
        next(); assertEquals(1, sent.size());
        runner.cancel("disabled");
        next(); assertEquals(1, sent.size());
        assertEquals(BatchRunner.State.CANCELLED, runner.snapshot().state());
    }
    @Test void reentrantDisableCompletesAuditAfterDispatchReturns() {
        var holder = new BatchRunner[1];
        holder[0] = runner(command -> { holder[0].cancel("disabled"); return true; });
        holder[0].start(batch); next();
        assertEquals(BatchRunner.State.CANCELLED, holder[0].snapshot().state());
        assertEquals(1, holder[0].snapshot().dispatched());
        assertFalse(holder[0].dispatching());
        var order = inOrder(observer);
        order.verify(observer).started(batch);
        order.verify(observer).attempted(batch.commands().getFirst(), true, "");
        order.verify(observer).finished(holder[0].snapshot(), "disabled");
        assertTrue(tasks.isEmpty());
    }
    @Test void rejectsConcurrentStartsAndIdleCancel() {
        var runner = runner(command -> true);
        assertFalse(runner.cancel("nothing"));
        runner.start(batch);
        assertThrows(IllegalStateException.class, () -> runner.start(batch));
    }
    @Test void auditFailureStopsAfterAnAcceptedDispatch() {
        doThrow(new IllegalStateException("disk full")).when(observer).attempted(any(), anyBoolean(), anyString());
        var runner = runner(command -> true);
        runner.start(batch); next();
        assertEquals(1, runner.snapshot().dispatched());
        assertEquals(BatchRunner.State.FAILED, runner.snapshot().state());
        assertTrue(tasks.isEmpty());
    }
    @Test void schedulerFailureDoesNotLeaveTheRunnerActive() {
        var runner = new BatchRunner((task, delay) -> { throw new IllegalStateException("disabled"); }, command -> true, observer, 20, 5);
        runner.start(batch);
        assertFalse(runner.active());
        assertEquals(BatchRunner.State.FAILED, runner.snapshot().state());
    }
}
