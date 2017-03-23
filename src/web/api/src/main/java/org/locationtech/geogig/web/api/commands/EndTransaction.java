/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.PagedMergeScenarioConsumer;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the TransactionEnd operation in GeoGig.
 * 
 * Web interface for {@link TransactionEnd}
 */

public class EndTransaction extends AbstractWebAPICommand {

    boolean cancel;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setCancel(Boolean.valueOf(options.getFirstValue("cancel", "false")));
    }

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
    protected void runInternal(CommandContext context) {
        final Context transaction = this.getRepositoryContext(context);

        TransactionEnd endTransaction = context.getRepository().command(TransactionEnd.class);
        try {
            endTransaction.setCancel(cancel).setTransaction((GeogigTransaction) transaction).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTransactionId(getTransactionId());
                    out.finish();
                }
            });
        } catch (MergeConflictsException m) {
            final RevCommit ours = context.getRepository().getCommit(m.getOurs());
            final RevCommit theirs = context.getRepository().getCommit(m.getTheirs());
            final Optional<ObjectId> ancestor = transaction.command(FindCommonAncestor.class)
                    .setLeft(ours).setRight(theirs).call();
            final PagedMergeScenarioConsumer consumer = new PagedMergeScenarioConsumer(0);
            final MergeScenarioReport report = transaction.command(ReportMergeScenarioOp.class)
                    .setMergeIntoCommit(ours).setToMergeCommit(theirs).setConsumer(consumer).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    Optional<RevCommit> mergeCommit = Optional.absent();
                    out.writeMergeConflictsResponse(mergeCommit, report, transaction, ours.getId(),
                            theirs.getId(), ancestor.get(), consumer);
                    out.finish();
                }
            });
        }
    }
}
