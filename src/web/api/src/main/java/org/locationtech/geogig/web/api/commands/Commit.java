/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Commit operation in GeoGig.
 * 
 * Web interface for {@link CommitOp}
 */
public class Commit extends AbstractWebAPICommand {

    String message;

    boolean all;

    Optional<String> authorName = Optional.absent();

    Optional<String> authorEmail = Optional.absent();

    public Commit(ParameterSet options) {
        super(options);
        setAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        setMessage(options.getFirstValue("message", null));
        setAuthorName(options.getFirstValue("authorName", null));
        setAuthorEmail(options.getFirstValue("authorEmail", null));
    }

    /**
     * Mutator for the message variable
     * 
     * @param message - the message for this commit
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Mutator for the all option
     * 
     * @param all - true to the commit everything in the working tree
     */
    public void setAll(boolean all) {
        this.all = all;
    }

    /**
     * @param authorName the author of the merge commit
     */
    public void setAuthorName(@Nullable String authorName) {
        this.authorName = Optional.fromNullable(authorName);
    }

    /**
     * @param authorEmail the email of the author of the merge commit
     */
    public void setAuthorEmail(@Nullable String authorEmail) {
        this.authorEmail = Optional.fromNullable(authorEmail);
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, commit requires a transaction to preserve the stability of the repository.");
        }
        final Context geogig = this.getCommandLocator(context);
        RevCommit commit;
        commit = geogig.command(CommitOp.class).setAuthor(authorName.orNull(), authorEmail.orNull())
                .setMessage(message).setAllowEmpty(true).setAll(all).call();

        final RevCommit commitToWrite = commit;
        final ObjectId parentId = commit.parentN(0).or(ObjectId.NULL);
        final AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                .setOldVersion(parentId).setNewVersion(commit.getId()).call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeCommitResponse(commitToWrite, diff);
                out.finish();
            }

            @Override
            public void close() {
                diff.close();
            }
        });
    }
}
