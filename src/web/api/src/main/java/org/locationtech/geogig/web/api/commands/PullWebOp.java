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

import java.util.Iterator;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;
import org.locationtech.geogig.api.porcelain.PullOp;
import org.locationtech.geogig.api.porcelain.PullResult;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Pull operation in GeoGig.
 * 
 * Web interface for {@link PullOp}
 */

public class PullWebOp extends AbstractWebAPICommand {

    private String remoteName;

    private boolean fetchAll;

    private String refSpec;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

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
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);

        PullOp command = geogig.command(PullOp.class)
                .setAuthor(authorName.orNull(), authorEmail.orNull()).setRemote(remoteName)
                .setAll(fetchAll).addRefSpec(refSpec);
        try {
            final PullResult result = command.call();
            final Iterator<DiffEntry> iter;
            if (result.getOldRef() != null && result.getNewRef() != null
                    && result.getOldRef().equals(result.getNewRef())) {
                iter = null;
            } else {
                if (result.getOldRef() == null) {
                    iter = geogig.command(DiffOp.class)
                            .setNewVersion(result.getNewRef().getObjectId())
                            .setOldVersion(ObjectId.NULL).call();
                } else {
                    iter = geogig.command(DiffOp.class)
                            .setNewVersion(result.getNewRef().getObjectId())
                            .setOldVersion(result.getOldRef().getObjectId()).call();
                }
            }

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writePullResponse(result, iter, geogig);
                    out.finish();
                }
            });
        } catch (SynchronizationException e) {
            switch (e.statusCode) {
            case HISTORY_TOO_SHALLOW:
            default:
                context.setResponseContent(CommandResponse
                        .error("Unable to pull, the remote history is shallow."));
            }
        } catch (MergeConflictsException e) {
            String[] refs = refSpec.split(":");
            String remoteRef = Ref.REMOTES_PREFIX + remoteName + "/" + refs[0];
            Optional<Ref> sourceRef = geogig.command(RefParse.class).setName(remoteRef).call();
            String destinationref = "";
            if (refs.length == 2) {
                destinationref = refs[1];
            } else {
                final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD)
                        .call();
                if (!currHead.isPresent()) {
                    context.setResponseContent(CommandResponse
                            .error("Repository has no HEAD, can't pull."));
                } else if (!(currHead.get() instanceof SymRef)) {
                    context.setResponseContent(CommandResponse
                            .error("Can't pull from detached HEAD"));
                }
                final SymRef headRef = (SymRef) currHead.get();
                destinationref = headRef.getTarget();
            }

            Optional<Ref> destRef = geogig.command(RefParse.class).setName(destinationref).call();
            final RevCommit theirs = context.getGeoGIG().getRepository()
                    .getCommit(sourceRef.get().getObjectId());
            final RevCommit ours = context.getGeoGIG().getRepository()
                    .getCommit(destRef.get().getObjectId());
            final Optional<ObjectId> ancestor = geogig.command(FindCommonAncestor.class)
                    .setLeft(ours).setRight(theirs).call();
            context.setResponseContent(new CommandResponse() {
                final MergeScenarioReport report = geogig.command(ReportMergeScenarioOp.class)
                        .setMergeIntoCommit(ours).setToMergeCommit(theirs).call();

                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    Optional<RevCommit> mergeCommit = Optional.absent();
                    out.writeMergeResponse(mergeCommit, report, geogig, ours.getId(),
                            theirs.getId(), ancestor.get());
                    out.finish();
                }
            });
        }
    }
}
