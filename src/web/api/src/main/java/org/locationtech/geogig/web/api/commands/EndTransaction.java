/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;
import org.locationtech.geogig.api.porcelain.RebaseConflictsException;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the TransactionEnd operation in GeoGig.
 * 
 * Web interface for {@link TransactionEnd}
 */

public class EndTransaction extends AbstractWebAPICommand {

    private boolean cancel;

    /**
     * Mutator for the cancel variable
     * 
     * @param cancel - true to abort all changes made in this transaction
     */
    public void setCancel(boolean cancel) {
        this.cancel = cancel;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException("There isn't a transaction to end.");
        }

        final Context transaction = this.getCommandLocator(context);

        TransactionEnd endTransaction = context.getGeoGIG().command(TransactionEnd.class);
        try {
            final boolean closed = endTransaction.setCancel(cancel)
                    .setTransaction((GeogigTransaction) transaction).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    if (closed) {
                        out.writeTransactionId(null);
                    } else {
                        out.writeTransactionId(getTransactionId());
                    }
                    out.finish();
                }
            });
        } catch (MergeConflictsException m) {
            final RevCommit ours = context.getGeoGIG().getRepository().getCommit(m.getOurs());
            final RevCommit theirs = context.getGeoGIG().getRepository().getCommit(m.getTheirs());
            final Optional<ObjectId> ancestor = transaction.command(FindCommonAncestor.class)
                    .setLeft(ours).setRight(theirs).call();
            context.setResponseContent(new CommandResponse() {
                final MergeScenarioReport report = transaction.command(ReportMergeScenarioOp.class)
                        .setMergeIntoCommit(ours).setToMergeCommit(theirs).call();

                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    Optional<RevCommit> mergeCommit = Optional.absent();
                    out.writeMergeResponse(mergeCommit, report, transaction, ours.getId(),
                            theirs.getId(), ancestor.get());
                    out.finish();
                }
            });
        } catch (RebaseConflictsException r) {
            // TODO: Handle rebase exception
        }
    }
}
