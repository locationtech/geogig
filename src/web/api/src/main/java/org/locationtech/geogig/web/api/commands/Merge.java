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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.PagedMergeScenarioConsumer;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Merge operation in GeoGig.
 * 
 * Web interface for {@link MergeOp}
 */

public class Merge extends AbstractWebAPICommand {

    boolean noCommit;

    String commit;

    Optional<String> authorName = Optional.absent();

    Optional<String> authorEmail = Optional.absent();

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setNoCommit(Boolean.valueOf(options.getFirstValue("noCommit", "false")));
        setCommit(options.getRequiredValue("commit"));
        setAuthorName(options.getFirstValue("authorName", null));
        setAuthorEmail(options.getFirstValue("authorEmail", null));
    }

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
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();
        if (!currHead.isPresent()) {
            throw new CommandSpecException("Repository has no HEAD, can't merge.");
        }

        MergeOp merge = geogig.command(MergeOp.class);
        merge.setAuthor(authorName.orNull(), authorEmail.orNull());

        final Optional<ObjectId> oid = geogig.command(RevParse.class).setRefSpec(commit)
                .call();
        if (oid.isPresent()) {
            merge.addCommit(oid.get());
        } else {
            throw new CommandSpecException("Couldn't resolve '" + commit + "' to a commit.");
        }

        try {
            final MergeReport report = merge.setNoCommit(noCommit).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeMergeResponse(Optional.fromNullable(report.getMergeCommit()),
                            report.getReport().get(), report.getOurs(),
                            report.getPairs().get(0).getTheirs(),
                            report.getPairs().get(0).getAncestor());
                    out.finish();
                }
            });
        } catch (Exception e) {
            Repository repository = context.getRepository();
            final RevCommit ours = repository.getCommit(currHead.get().getObjectId());
            final RevCommit theirs = repository.getCommit(oid.get());
            final Optional<ObjectId> ancestor = geogig.command(FindCommonAncestor.class)
                    .setLeft(ours).setRight(theirs).call();
            final PagedMergeScenarioConsumer consumer = new PagedMergeScenarioConsumer(0);
            final MergeScenarioReport report = geogig.command(ReportMergeScenarioOp.class)
                    .setMergeIntoCommit(ours).setConsumer(consumer).setToMergeCommit(theirs).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    Optional<RevCommit> mergeCommit = Optional.absent();
                    out.writeMergeConflictsResponse(mergeCommit, report, geogig, ours.getId(),
                            theirs.getId(), ancestor.get(), consumer);
                    out.finish();
                }
            });

        }
    }
}
