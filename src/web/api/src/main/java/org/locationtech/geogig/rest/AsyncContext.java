package org.locationtech.geogig.rest;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.plumbing.TransactionBegin;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class AsyncContext {

    public static final String CONTEXT_KEY = "GeoGigAsyncContext";

    public static enum Status {
        WAITING, RUNNING, FINISHED, FAILED, CANCELLED
    }

    private static AsyncContext INSTANCE;

    public static synchronized AsyncContext get() {
        if (INSTANCE == null) {
            INSTANCE = new AsyncContext();
        }
        return INSTANCE;
    }

    private Map<String, AsyncCommand<?>> commands = new ConcurrentHashMap<>();

    private ScheduledExecutorService commandExecutor;

    private AsyncContext() {
        int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                .setNameFormat("GeoGIG async tasks-%d").build();
        this.commandExecutor = Executors.newScheduledThreadPool(nThreads, threadFactory);
        this.commandExecutor.scheduleAtFixedRate(new PruneTask(), 0, 10, TimeUnit.MINUTES);
    }

    private class PruneTask implements Runnable {

        @Override
        public void run() {
            Iterable<AsyncCommand<? extends Object>> all = AsyncContext.this.getAll();
            for (AsyncCommand<?> c : all) {
                if (c.isDone()) {
                    AsyncContext.this.commands.remove(c.getTaskId());
                }
            }
        }

    }

    public <T> AsyncCommand<T> run(AbstractGeoGigOp<T> command, String description) {
        return run(command, false, description);
    }

    public <T> AsyncCommand<T> runInTransaction(AbstractGeoGigOp<T> command, String description) {
        return run(command, true, description);
    }

    private <T> AsyncCommand<T> run(AbstractGeoGigOp<T> command, boolean transaction,
            String description) {
        CommandCall<T> callable = new CommandCall<T>(command, transaction);
        Future<T> future = commandExecutor.submit(callable);
        AsyncCommand<T> asyncCommand = new AsyncCommand<T>(callable, future, description);
        commands.put(asyncCommand.getTaskId(), asyncCommand);
        return asyncCommand;
    }

    public Optional<AsyncCommand<?>> getAndPruneIfFinished(final String taskId) {
        Optional<AsyncCommand<?>> cmd = get(taskId);
        if (cmd.isPresent() && cmd.get().isDone()) {
            commands.remove(taskId);
        }
        return cmd;
    }

    public Optional<AsyncCommand<?>> get(final String taskId) {
        AsyncCommand<?> asyncCommand = commands.get(taskId);
        return Optional.<AsyncCommand<?>> fromNullable(asyncCommand);
    }

    public static class AsyncCommand<T> {

        private static AtomicLong ID_SEQ = new AtomicLong();

        private CommandCall<T> command;

        private Future<T> future;

        private final String taskId;

        private String description;

        public AsyncCommand(CommandCall<T> command, Future<T> future, String description) {
            this.command = command;
            this.future = future;
            this.description = description;
            this.taskId = String.valueOf(ID_SEQ.incrementAndGet());
        }

        public Optional<UUID> getTransactionId() {
            if (command.tx == null) {
                return Optional.absent();
            }
            return Optional.of(command.tx.getTransactionId());
        }

        public Status getStatus() {
            return command.status;
        }

        public String getStatusLine() {
            return command.progress.getDescription();
        }

        public float getProgress() {
            return command.progress.getProgress();
        }

        public boolean isDone() {
            return future.isDone();
        }

        public T get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        public String getTaskId() {
            return taskId;
        }

        public String getDescription() {
            return description;
        }

        @SuppressWarnings("unchecked")
        public Class<? extends AbstractGeoGigOp<?>> getCommandClass() {
            return (Class<? extends AbstractGeoGigOp<?>>) command.commandClass;
        }

        public void tryCancel() {
            if (!isDone()) {
                command.command.getProgressListener().cancel();
            }
        }
    }

    private static class CommandCall<T> implements Callable<T> {
        private AbstractGeoGigOp<T> command;

        private final Class<?> commandClass;

        private Status status;

        private GeogigTransaction tx;

        private boolean wrapInTransaction;

        private DefaultProgressListener progress = new DefaultProgressListener();

        public CommandCall(AbstractGeoGigOp<T> command, boolean wrapInTransaction) {
            this.command = command;
            this.commandClass = command.getClass();
            this.wrapInTransaction = wrapInTransaction;
            this.status = Status.WAITING;
        }

        @Override
        public T call() throws Exception {
            if (command.getProgressListener().isCanceled()) {
                this.status = Status.CANCELLED;
                return null;
            }
            this.status = Status.RUNNING;
            try {
                if (wrapInTransaction) {
                    this.tx = command.context().command(TransactionBegin.class).call();
                    command.setContext(this.tx);
                }
                command.setProgressListener(progress);
                T result = command.call();
                if (command.getProgressListener().isCanceled()) {
                    this.status = Status.CANCELLED;
                    if (tx != null) {
                        tx.abort();
                    }
                } else {
                    if (tx != null) {
                        tx.commit();
                    }
                    this.status = Status.FINISHED;
                }
                return result;
            } catch (Throwable e) {
                this.status = Status.FAILED;
                if (tx != null) {
                    tx.abort();
                }
                throw e;
            } finally {
                command = null;
            }
        }
    }

    public Iterable<AsyncCommand<? extends Object>> getAll() {
        return new ArrayList<>(commands.values());
    }

}
