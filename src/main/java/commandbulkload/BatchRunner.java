// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: sequential dispatch with cancellation and honest result counts.
package commandbulkload;

public final class BatchRunner {
    public enum State { IDLE, RUNNING, COMPLETED, CANCELLED, FAILED }
    public record Snapshot(State state, String file, int total, int attempted, int dispatched, int errors, int lastLine) {
        public int remaining() { return total - attempted; }
    }
    @FunctionalInterface public interface CancelTask { void cancel(); }
    @FunctionalInterface public interface Scheduler { CancelTask later(Runnable task, long ticks); }
    @FunctionalInterface public interface Dispatcher { boolean dispatch(String command); }
    public interface Observer {
        void started(BatchParser.Batch batch);
        void attempted(BatchParser.CommandLine command, boolean accepted, String error);
        void progress(Snapshot snapshot);
        void finished(Snapshot snapshot, String reason);
    }

    private final Scheduler scheduler;
    private final Dispatcher dispatcher;
    private final Observer observer;
    private final long interval;
    private final int progressEvery;
    private BatchParser.Batch batch;
    private State state = State.IDLE;
    private int attempted, dispatched, errors, lastLine;
    private long generation;
    private CancelTask pending;
    private boolean dispatching;
    private String deferredReason;

    public BatchRunner(Scheduler scheduler, Dispatcher dispatcher, Observer observer, long interval, int progressEvery) {
        if (interval < 1 || progressEvery < 1) throw new IllegalArgumentException("Positive interval and progress count required.");
        this.scheduler = scheduler;
        this.dispatcher = dispatcher;
        this.observer = observer;
        this.interval = interval;
        this.progressEvery = progressEvery;
    }

    public boolean dispatching() { return dispatching; }
    public boolean active() { return state == State.RUNNING; }
    public Snapshot snapshot() {
        return new Snapshot(state, batch == null ? "-" : batch.filename(), batch == null ? 0 : batch.commands().size(),
                attempted, dispatched, errors, lastLine);
    }
    public void start(BatchParser.Batch next) {
        if (active()) throw new IllegalStateException("A batch is already running.");
        if (next.commands().isEmpty()) throw new IllegalArgumentException("Empty batch.");
        batch = next;
        attempted = dispatched = errors = lastLine = 0;
        state = State.RUNNING;
        long token = ++generation;
        try {
            observer.started(batch);
            schedule(token, 1);
        } catch (RuntimeException failure) {
            finish(State.FAILED, "Could not start: " + failure.getMessage());
        }
    }
    public boolean cancel(String reason) {
        if (!active()) return false;
        if (dispatching) { state = State.CANCELLED; deferredReason = reason; }
        else finish(State.CANCELLED, reason);
        return true;
    }
    private void schedule(long token, long delay) {
        pending = scheduler.later(() -> step(token), delay);
    }
    private void step(long token) {
        if (!active() || generation != token) return;
        pending = null;
        var command = batch.commands().get(attempted);
        lastLine = command.line();
        attempted++;
        boolean accepted = false;
        String reason = "";
        dispatching = true;
        try { accepted = dispatcher.dispatch(command.command()); }
        catch (RuntimeException failure) { reason = failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
        finally { dispatching = false; }
        if (accepted) dispatched++; else errors++;
        if (!accepted && reason.isEmpty()) reason = "Dispatch returned false (unknown command or dispatch failure).";
        try { observer.attempted(command, accepted, reason); }
        catch (RuntimeException failure) {
            finish(State.FAILED, "Audit failure after line " + lastLine + ": " + failure.getMessage());
            return;
        }
        if (deferredReason != null) {
            String deferred = deferredReason;
            deferredReason = null;
            finish(State.CANCELLED, deferred);
            return;
        }
        // A command can disable the plugin or request cancellation during its own dispatch.
        if (!active() || generation != token) return;
        if (!accepted) { finish(State.FAILED, reason); return; }
        if (attempted == batch.commands().size()) { finish(State.COMPLETED, ""); return; }
        try {
            if (attempted % progressEvery == 0) observer.progress(snapshot());
            if (active() && generation == token) schedule(token, interval);
        } catch (RuntimeException failure) { finish(State.FAILED, "Scheduling/reporting failure: " + failure.getMessage()); }
    }
    private void finish(State end, String reason) {
        state = end;
        generation++;
        if (pending != null) { pending.cancel(); pending = null; }
        observer.finished(snapshot(), reason);
    }
}
