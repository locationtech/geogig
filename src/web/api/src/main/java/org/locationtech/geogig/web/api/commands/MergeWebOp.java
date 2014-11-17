/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * Interface for the Merge operation in GeoGig.
 * 
 * Web interface for {@link MergeOp}
 */

public class MergeWebOp extends AbstractWebAPICommand {

    private boolean noCommit;

    private String commit;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    /**
     * Mutator for the noCommit variable
     * 
     * @param noCommit - true to merge without creating a commit afterwards
     */
    public void setNoCommit(boolean noCommit) {
        this.noCommit = noCommit;
    }

    /**
     * Mutator for the commit variable
     * 
     * @param commit - the commit to merge into the baseRef or the currently checked out ref
     */
    public void setCommit(String commit) {
        this.commit = commit;
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
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, merge requires a transaction to preserve the stability of the repository.");
        } else if (this.commit == null) {
            throw new CommandSpecException("No commits were specified for merging.");
        }

        final GeogigTransaction transaction = (GeogigTransaction) this.getCommandLocator(context);

        final Optional<Ref> currHead = transaction.command(RefParse.class).setName(Ref.HEAD).call();
        if (!currHead.isPresent()) {
            throw new CommandSpecException("Repository has no HEAD, can't merge.");
        }

        MergeOp merge = transaction.command(MergeOp.class);
        merge.setAuthor(authorName.orNull(), authorEmail.orNull());

        final Optional<ObjectId> oid = transaction.command(RevParse.class).setRefSpec(commit)
                .call();
        if (oid.isPresent()) {
            merge.addCommit(Suppliers.ofInstance(oid.get()));
        } else {
            throw new CommandSpecException("Couldn't resolve '" + commit + "' to a commit.");
        }

        try {
            final MergeReport report = merge.setNoCommit(noCommit).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeMergeResponse(Optional.fromNullable(report.getMergeCommit()), report
                            .getReport().get(), transaction, report.getOurs(), report.getPairs()
                            .get(0).getTheirs(), report.getPairs().get(0).getAncestor());
                    out.finish();
                }
            });
        } catch (Exception e) {
            final RevCommit ours = context.getGeoGIG().getRepository()
                    .getCommit(currHead.get().getObjectId());
            final RevCommit theirs = context.getGeoGIG().getRepository().getCommit(oid.get());
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

        }
    }
}
