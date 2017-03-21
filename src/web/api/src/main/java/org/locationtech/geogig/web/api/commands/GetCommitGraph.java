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

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * Lists all commits from a given commitId through to a certain depth.
 * 
 */

public class GetCommitGraph extends AbstractWebAPICommand {

    String commitId;

    int depth;

    int page;

    int elementsPerPage;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setDepth(parseInt(options, "depth", 0));
        setCommitId(options.getRequiredValue("commitId"));
        setPage(parseInt(options, "page", 0));
        setElementsPerPage(parseInt(options, "show", 30));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the commitId variable
     * 
     * @param commitId - the id of the commit to start at
     */
    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    /**
     * Mutator for the depth variable
     * 
     * @param depth - the depth to search to
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Mutator for the page variable
     * 
     * @param page - the page number to build in the response
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * Mutator for the elementsPerPage variable
     * 
     * @param elementsPerPage - the number of elements to list per page
     */
    public void setElementsPerPage(int elementsPerPage) {
        this.elementsPerPage = elementsPerPage;
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
        if (commitId.equals(ObjectId.NULL.toString())) {
            throw new CommandSpecException("Invalid commitId was given.");
        }
        final Repository geogig = context.getRepository();
        RevCommit commit = geogig.getCommit(ObjectId.valueOf(commitId));
        final List<RevCommit> history = Lists.newLinkedList();

        List<CommitNode> nodes = Lists.newLinkedList();
        CommitNode node = new CommitNode(commit, 1);
        nodes.add(node);

        while (!nodes.isEmpty()) {
            node = nodes.remove(0);
            if (!history.contains(node.commit)) {
                history.add(node.commit);
            }
            if (this.depth == 0 || node.depth < this.depth) {
                for (ObjectId id : node.commit.getParentIds()) {
                    nodes.add(new CommitNode(geogig.getCommit(id), node.depth + 1));
                }
            }
        }

        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                final Iterator<RevCommit> historyIterator = history.iterator();
                Iterators.advance(historyIterator, page * elementsPerPage);
                out.start();
                out.writeCommits(historyIterator, elementsPerPage, false);
                out.finish();
            }
        });
    }

    /**
     * Private helper class to store the information needed to traverse the commit graph properly.
     * 
     */
    private class CommitNode {
        public RevCommit commit;

        public int depth;

        CommitNode(RevCommit commit, int depth) {
            this.commit = commit;
            this.depth = depth;
        }
    }
}
