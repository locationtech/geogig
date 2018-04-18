/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.geotools.util.Range;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.ParseTimestamp;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * List certain statistics of repository.
 */

public class Statistics extends AbstractWebAPICommand {

    String path;

    String since;

    String until;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setPath(options.getFirstValue("path", null));
        setSince(options.getFirstValue("since", null));
        setUntil(options.getFirstValue("branch", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public void setUntil(String until) {
        this.until = until;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);
        final List<FeatureTypeStats> stats = Lists.newArrayList();
        LogOp logOp = geogig.command(LogOp.class).setFirstParentOnly(true);
        LsTreeOp lsTreeOp = geogig.command(LsTreeOp.class)
                .setStrategy(LsTreeOp.Strategy.TREES_ONLY);
        final Iterator<RevCommit> log;
        if (since != null && !since.trim().isEmpty()) {
            Date untilTime = new Date();
            Date sinceTime = new Date(geogig.command(ParseTimestamp.class).setString(since).call());
            logOp.setTimeRange(new Range<Date>(Date.class, sinceTime, untilTime));
        }
        if (this.until != null) {
            Optional<ObjectId> until;
            until = geogig.command(RevParse.class).setRefSpec(this.until).call();
            Preconditions.checkArgument(until.isPresent(), "Object not found '%s'", this.until);
            logOp.setUntil(until.get());
            lsTreeOp.setReference(this.until);
        }

        if (path != null && !path.trim().isEmpty()) {
            logOp.addPath(path);
        }
        final Iterator<NodeRef> treeIter = lsTreeOp.call();

        while (treeIter.hasNext()) {
            NodeRef node = treeIter.next();
            if (path == null || path.trim().isEmpty() || node.path().startsWith(path)) {
                stats.add(new FeatureTypeStats(node.path(),
                        context.getRepository().getTree(node.getObjectId()).size()));
            }
        }
        log = logOp.call();

        RevCommit firstCommit = null;
        RevCommit lastCommit = null;
        int totalCommits = 0;
        final List<RevPerson> authors = Lists.newArrayList();

        if (log.hasNext()) {
            lastCommit = log.next();
            authors.add(lastCommit.getAuthor());
            totalCommits++;
        }
        while (log.hasNext()) {
            firstCommit = log.next();
            RevPerson newAuthor = firstCommit.getAuthor();
            // If the author isn't defined, use the committer for the purposes of statistics.
            if (!newAuthor.getName().isPresent() && !newAuthor.getEmail().isPresent()) {
                newAuthor = firstCommit.getCommitter();
            }
            if (newAuthor.getName().isPresent() || newAuthor.getEmail().isPresent()) {
                boolean authorFound = false;
                for (RevPerson author : authors) {
                    if (newAuthor.getName().equals(author.getName())
                            && newAuthor.getEmail().equals(author.getEmail())) {
                        authorFound = true;
                        break;
                    }
                }
                if (!authorFound) {
                    authors.add(newAuthor);
                }
            }
            totalCommits++;
        }
        int addedFeatures = 0;
        int modifiedFeatures = 0;
        int removedFeatures = 0;
        if (since != null && !since.trim().isEmpty() && firstCommit != null && lastCommit != null) {
            try (final AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                    .setOldVersion(firstCommit.getId()).setNewVersion(lastCommit.getId())
                    .setFilter(path).call()) {
                while (diff.hasNext()) {
                    DiffEntry entry = diff.next();
                    if (entry.changeType() == DiffEntry.ChangeType.ADDED) {
                        addedFeatures++;
                    } else if (entry.changeType() == DiffEntry.ChangeType.MODIFIED) {
                        modifiedFeatures++;
                    } else {
                        removedFeatures++;
                    }
                }
            }
        }

        final RevCommit first = firstCommit;
        final RevCommit last = lastCommit;
        final int total = totalCommits;
        final int added = addedFeatures;
        final int modified = modifiedFeatures;
        final int removed = removedFeatures;
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start(true);
                out.writeStatistics(stats, first, last, total, authors, added, modified, removed);
                out.finish();
            }
        });
    }

    public class FeatureTypeStats {
        long numFeatures;

        String featureTypeName;

        public FeatureTypeStats(String name, long numFeatures) {
            this.numFeatures = numFeatures;
            this.featureTypeName = name;
        }

        public long getNumFeatures() {
            return numFeatures;
        }

        public String getName() {
            return featureTypeName;
        }
    };
}
