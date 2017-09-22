/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
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

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class AsyncContext {

    public static final String CONTEXT_KEY = "GeoGigAsyncContext";

    public static enum Status {
        WAITING {
            @Override
            public boolean isTerminated() {
                return false;
            }
        },
        RUNNING {
            @Override
            public boolean isTerminated() {
                return false;
            }
        },
        FINISHED {
            @Override
            public boolean isTerminated() {
                return true;
            }
        },
        FAILED {
            @Override
            public boolean isTerminated() {
                return true;
            }
        },
        CANCELLED {
            @Override
            public boolean isTerminated() {
                return true;
            }
        };

        public abstract boolean isTerminated();
    }

    private static AsyncContext INSTANCE;

    public static synchronized AsyncContext get() {
        if (INSTANCE == null) {
            INSTANCE = new AsyncContext();
        }
        return INSTANCE;
    }

    public static synchronized void close() {
        if (INSTANCE != null) {
            INSTANCE.shutDown();
            INSTANCE = null;
        }
    }

    private Map<String, AsyncCommand<?>> commands = new ConcurrentHashMap<>();

    private ScheduledExecutorService commandExecutor;

    private AtomicLong ID_SEQ = new AtomicLong();

    private AsyncContext() {
        int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                .setNameFormat("GeoGIG async tasks-%d").build();
        this.commandExecutor = Executors.newScheduledThreadPool(nThreads, threadFactory);
        this.commandExecutor.scheduleAtFixedRate(new PruneTask(), 0, 10, TimeUnit.MINUTES);
    }

    @VisibleForTesting
    public static AsyncContext createNew() {
        return new AsyncContext();
    }

    @VisibleForTesting
    public void shutDown() {
        commandExecutor.shutdown();
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

        CommandCall<T> callable = new CommandCall<T>(command);
        Future<T> future = commandExecutor.submit(callable);
        String taskId = String.valueOf(ID_SEQ.incrementAndGet());
        AsyncCommand<T> asyncCommand = new AsyncCommand<T>(taskId, callable, future, description);
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

        private final CommandCall<T> command;

        private final Future<T> future;

        private final String taskId;

        private String description;

        public AsyncCommand(String taskId, CommandCall<T> command, Future<T> future,
                String description) {
            this.command = command;
            this.future = future;
            this.description = description;
            this.taskId = taskId;
        }

        public Optional<UUID> getTransactionId() {
            Context context = command.command.context();
            if (context instanceof GeogigTransaction) {
                GeogigTransaction tx = (GeogigTransaction) context;
                UUID txId = tx.getTransactionId();
                return Optional.of(txId);
            }
            return Optional.absent();
        }

        public Context getContext() {
            return command.command.context();
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

        public ProgressListener getProgressListener() {
            return command.progress;
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

        /**
         * Clean up any resources used by the command results, if applicable.
         */
        public void close() {
            if (future.isDone()) {
                try {
                    T result = future.get();
                    if (result instanceof AutoCloseable) {
                        ((AutoCloseable) result).close();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) cause).close();
                        } catch (Exception ex) {
                            // Do nothing
                        }
                    }
                }
            }
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

        private final AbstractGeoGigOp<T> command;

        private final Class<?> commandClass;

        private Status status;

        private final DefaultProgressListener progress = new DefaultProgressListener();

        public CommandCall(AbstractGeoGigOp<T> command) {
            this.command = command;
            this.commandClass = command.getClass();
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
                command.setProgressListener(progress);
                T result = command.call();
                if (command.getProgressListener().isCanceled()) {
                    this.status = Status.CANCELLED;
                } else {
                    this.status = Status.FINISHED;
                }
                return result;
            } catch (Throwable e) {
                this.status = Status.FAILED;
                throw e;
            }
        }
    }

    public Iterable<AsyncCommand<? extends Object>> getAll() {
        return new ArrayList<>(commands.values());
    }

}
