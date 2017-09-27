/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.hash.Hashing;

/**
 * Allows a user to delete a repository.
 */

public class RequestDeleteRepositoryToken extends AbstractWebAPICommand {
    private static ScheduledExecutorService deleteTokenExecutor = Executors
            .newSingleThreadScheduledExecutor();

    @Override
    protected void setParametersInternal(ParameterSet options) {
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    public static String deleteKeyForToken(String token) {
        return "command/delete/" + token;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        SecureRandom rnd = new SecureRandom();
        byte[] bytes = new byte[128];
        rnd.nextBytes(bytes);
        String deleteToken = Hashing.sipHash24().hashBytes(bytes).toString();

        final String deleteKey = deleteKeyForToken(deleteToken);

        final long now = geogig.platform().currentTimeMillis();
        byte[] nowBytes = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(now).array();

        final BlobStore blobStore = geogig.repository().blobStore();
        blobStore.putBlob(deleteKey, nowBytes);
        deleteTokenExecutor.schedule(new Runnable() {

            private Repository repo = context.getRepository();

            @Override
            public void run() {
                if (repo.isOpen()) {
                    BlobStore blobs = repo.blobStore();
                    blobs.removeBlob(deleteKey);
                }

            }
        }, 60, TimeUnit.SECONDS);

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("token", deleteToken);
                out.finish();
            }
        });
    }
}
