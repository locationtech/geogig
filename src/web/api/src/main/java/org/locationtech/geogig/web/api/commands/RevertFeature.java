/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static com.google.common.base.Preconditions.checkState;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateTree;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Web interface to revert a single feature to a preview state and create a commit
 */

public class RevertFeature extends AbstractWebAPICommand {

    String featurePath;

    String oldCommitId;

    String newCommitId;

    Optional<String> authorName = Optional.absent();

    Optional<String> authorEmail = Optional.absent();

    Optional<String> commitMessage = Optional.absent();

    Optional<String> mergeMessage = Optional.absent();

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setAuthorName(options.getFirstValue("authorName", null));
        setAuthorEmail(options.getFirstValue("authorEmail", null));
        setCommitMessage(options.getFirstValue("commitMessage", null));
        setMergeMessage(options.getFirstValue("mergeMessage", null));
        setNewCommitId(options.getRequiredValue("newCommitId"));
        setOldCommitId(options.getRequiredValue("oldCommitId"));
        setPath(options.getRequiredValue("path"));
    }

    /**
     * Mutator for the featurePath variable
     * 
     * @param featurePath - the path to the feature you want to revert
     */
    public void setPath(String featurePath) {
        this.featurePath = featurePath;
    }

    /**
     * Mutator for the oldCommitId variable
     * 
     * @param oldCommitId - the commit that contains the version of the feature to revert to
     */
    public void setOldCommitId(String oldCommitId) {
        this.oldCommitId = oldCommitId;
    }

    /**
     * Mutator for the newCommitId variable
     * 
     * @param newCommitId - the commit that contains the version of the feature that we want to undo
     */
    public void setNewCommitId(String newCommitId) {
        this.newCommitId = newCommitId;
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
     * @param commitMessage the commit message for the revert
     */
    public void setCommitMessage(@Nullable String commitMessage) {
        this.commitMessage = Optional.fromNullable(commitMessage);
    }

    /**
     * @param mergeMessage the message for the merge of the revert commit
     */
    public void setMergeMessage(@Nullable String mergeMessage) {
        this.mergeMessage = Optional.fromNullable(mergeMessage);
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

        ObjectId newCommitObjectId = ObjectId.valueOf(newCommitId);

        // get tree from new commit
        Optional<ObjectId> treeId = geogig.command(ResolveTreeish.class)
                .setTreeish(newCommitObjectId).call();

        Preconditions.checkState(treeId.isPresent(),
                "New commit id did not resolve to a valid tree.");
        final RevTree newTree = geogig.objectDatabase().getTree(treeId.get());

        ObjectId oldCommitObjectId = ObjectId.valueOf(oldCommitId);

        // get tree from old commit
        treeId = geogig.command(ResolveTreeish.class).setTreeish(oldCommitObjectId).call();

        Preconditions.checkState(treeId.isPresent(),
                "Old commit id did not resolve to a valid tree.");
        final RevTree oldTree = geogig.objectDatabase().getTree(treeId.get());

        // get feature from old tree
        NodeRef node = geogig.command(FindTreeChild.class).setParent(oldTree)
                .setChildPath(featurePath).call().orNull();
        boolean delete = false;
        if (node == null) {
            delete = true;
            node = geogig.command(FindTreeChild.class).setParent(newTree).setChildPath(featurePath)
                    .call().orNull();
            if (node == null) {
                throw new CommandSpecException("The feature was not found in either commit tree.");
            }
        }

        // get the new parent tree
        ObjectId metadataId = ObjectId.NULL;
        Optional<NodeRef> parentNode = geogig.command(FindTreeChild.class).setParent(newTree)
                .setChildPath(node.getParentPath()).call();

        CanonicalTreeBuilder treeBuilder;
        if (parentNode.isPresent()) {
            metadataId = parentNode.get().getMetadataId();
            Optional<RevTree> parsed = geogig.command(RevObjectParse.class)
                    .setObjectId(parentNode.get().getNode().getObjectId()).call(RevTree.class);
            checkState(parsed.isPresent(), "Parent tree couldn't be found in the repository.");
            treeBuilder = CanonicalTreeBuilder.create(geogig.objectDatabase(), parsed.get());
            treeBuilder.remove(node.getNode());
        } else {
            treeBuilder = CanonicalTreeBuilder.create(geogig.objectDatabase());
        }

        // put the old feature into the new tree
        if (!delete) {
            treeBuilder.put(node.getNode());
        }
        RevTree newFeatureTree = treeBuilder.build();

        NodeRef newTreeNode = NodeRef.tree(node.getParentPath(), newFeatureTree.getId(),
                metadataId);
        RevTree newRoot = geogig.command(UpdateTree.class).setRoot(newTree).setChild(newTreeNode)
                .call();

        // build new commit with parent of new commit and the newly built tree
        RevCommitBuilder builder = RevCommit.builder();

        builder.parentIds(Lists.newArrayList(newCommitObjectId));
        builder.treeId(newRoot.getId());
        builder.author(authorName.orNull());
        builder.authorEmail(authorEmail.orNull());
        builder.message(commitMessage
                .or("Reverted changes made to " + featurePath + " at " + newCommitId.toString()));

        RevCommit mapped = builder.build();
        Repository repository = context.getRepository();
        repository.objectDatabase().put(mapped);

        // merge commit into current branch
        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();
        if (!currHead.isPresent()) {
            throw new CommandSpecException("Repository has no HEAD, can't merge.");
        }

        MergeOp merge = geogig.command(MergeOp.class);
        merge.setAuthor(authorName.orNull(), authorEmail.orNull());
        merge.addCommit(mapped.getId());
        merge.setMessage(mergeMessage.or("Merged revert of " + featurePath));

        try {
            final MergeReport report = merge.call();

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
            final RevCommit ours = repository.getCommit(currHead.get().getObjectId());
            final RevCommit theirs = repository.getCommit(mapped.getId());
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
}
