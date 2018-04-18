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
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PullResult;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.PagedMergeScenarioConsumer;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Pull operation in GeoGig.
 * 
 * Web interface for {@link PullOp}
 */

public class Pull extends AbstractWebAPICommand {

    String remoteName;

    boolean fetchAll;

    String refSpec;

    Optional<String> authorName = Optional.absent();

    Optional<String> authorEmail = Optional.absent();

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setFetchAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        setRefSpec(options.getFirstValue("ref", null));
        setRemoteName(options.getFirstValue("remoteName", null));
        setAuthorName(options.getFirstValue("authorName", null));
        setAuthorEmail(options.getFirstValue("authorEmail", null));
    }

    /**
     * Mutator for the remoteName variable
     * 
     * @param remoteName - the name of the remote to pull from
     */
    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    /**
     * Mutator for the fetchAll variable
     * 
     * @param fetchAll - true to fetch all
     */
    public void setFetchAll(boolean fetchAll) {
        this.fetchAll = fetchAll;
    }

    /**
     * Mutator for the refSpec variable
     * 
     * @param refSpecs - the ref to pull
     */
    public void setRefSpec(String refSpec) {
        this.refSpec = refSpec;
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
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        PullOp command = geogig.command(PullOp.class)
                .setAuthor(authorName.orNull(), authorEmail.orNull()).setRemote(remoteName)
                .setAll(fetchAll).addRefSpec(refSpec);
        try {
            final PullResult result = command.call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    try (final AutoCloseableIterator<DiffEntry> iter = resolveDiff(geogig,
                            result)) {
                        out.start();
                        out.writePullResponse(result, iter);
                        out.finish();
                    }
                }
            });
        } catch (SynchronizationException e) {
            switch (e.statusCode) {
            case HISTORY_TOO_SHALLOW:
            default:
                throw new CommandSpecException("Unable to pull, the remote history is shallow.");
            }
        } catch (MergeConflictsException e) {
            String[] refs = refSpec.split(":");
            String remoteRef = Ref.REMOTES_PREFIX + remoteName + "/" + refs[0];
            Optional<Ref> sourceRef = geogig.command(RefParse.class).setName(remoteRef).call();
            String destinationref = "";
            if (refs.length == 2) {
                destinationref = refs[1];
            } else {
                final SymRef headRef = (SymRef) geogig.command(RefParse.class).setName(Ref.HEAD)
                        .call().get();
                destinationref = headRef.getTarget();
            }

            Optional<Ref> destRef = geogig.command(RefParse.class).setName(destinationref).call();
            Repository repository = context.getRepository();
            final RevCommit theirs = repository.getCommit(sourceRef.get().getObjectId());
            final RevCommit ours = repository.getCommit(destRef.get().getObjectId());
            final Optional<ObjectId> ancestor = geogig.command(FindCommonAncestor.class)
                    .setLeft(ours).setRight(theirs).call();
            final PagedMergeScenarioConsumer consumer = new PagedMergeScenarioConsumer(0);
            final MergeScenarioReport report = geogig.command(ReportMergeScenarioOp.class)
                    .setMergeIntoCommit(ours).setToMergeCommit(theirs).setConsumer(consumer).call();
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

    private AutoCloseableIterator<DiffEntry> resolveDiff(Context geogig, PullResult result) {
        AutoCloseableIterator<DiffEntry> iter;
        if (result.getOldRef() != null && result.getOldRef().equals(result.getNewRef())) {
            iter = null;
        } else {
            if (result.getOldRef() == null) {
                iter = geogig.command(DiffOp.class).setNewVersion(result.getNewRef().getObjectId())
                        .setOldVersion(ObjectId.NULL).call();
            } else {
                iter = geogig.command(DiffOp.class).setNewVersion(result.getNewRef().getObjectId())
                        .setOldVersion(result.getOldRef().getObjectId()).call();
            }
        }
        return iter;
    }
}
