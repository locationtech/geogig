/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import java.util.concurrent.CountDownLatch;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

public abstract class PRCommand<T> extends AbstractGeoGigOp<T> {

    private T prCommandResult;

    private RuntimeException error;

    private CountDownLatch latch = new CountDownLatch(1);

    protected PRCommand() {
        setProgressListener(new DefaultProgressListener());
        addListener(new Command.CommandListener() {
            public @Override void preCall(Command<?> command) {
            }

            @SuppressWarnings("unchecked")
            public @Override void postCall(Command<?> command, @Nullable Object result,
                    @Nullable RuntimeException exception) {
                PRCommand.this.prCommandResult = (T) result;
                PRCommand.this.error = exception;
                latch.countDown();
            }
        });
    }

    public void abort() {
        getProgressListener().cancel();
    }

    protected boolean isCancelled() {
        return getProgressListener().isCanceled();
    }

    public T await() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
        if (error != null) {
            throw error;
        }
        return prCommandResult;
    }

    protected String fmt(RevCommit c) {
        String msg = c.getMessage();
        if (msg.length() > 30) {
            msg = msg.substring(0, 30) + "...";
        }
        return String.format("%s (%s)", c.getId().toString().substring(0, 8), msg);
    }

}
