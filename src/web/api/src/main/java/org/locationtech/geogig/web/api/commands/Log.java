/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.io.Writer;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.geotools.util.Range;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ParseTimestamp;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.opengis.feature.type.PropertyDescriptor;
import org.springframework.http.MediaType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;

/**
 * Interface for the Log operation in GeoGig.
 * 
 * Web interface for {@link LogOp}
 */
public class Log extends AbstractWebAPICommand {

    Integer skip;

    Integer limit;

    String since;

    String until;

    String sinceTime;

    String untilTime;

    List<String> paths;

    int page;

    int elementsPerPage;

    boolean firstParentOnly;

    boolean countChanges = false;

    boolean returnRange = false;

    boolean summary = false;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setLimit(parseInt(options, "limit", null));
        setOffset(parseInt(options, "offset", null));
        setPaths(Arrays.asList(options.getValuesArray("path")));
        setSince(options.getFirstValue("since"));
        setUntil(options.getFirstValue("until"));
        setSinceTime(options.getFirstValue("sinceTime"));
        setUntilTime(options.getFirstValue("untilTime"));
        setPage(parseInt(options, "page", 0));
        setElementsPerPage(parseInt(options, "show", 30));
        setFirstParentOnly(Boolean.valueOf(options.getFirstValue("firstParentOnly", "false")));
        setCountChanges(Boolean.valueOf(options.getFirstValue("countChanges", "false")));
        setReturnRange(Boolean.valueOf(options.getFirstValue("returnRange", "false")));
        setSummary(Boolean.valueOf(options.getFirstValue("summary", "false")));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the limit variable
     * 
     * @param limit - the number of commits to print
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * Mutator for the offset variable
     * 
     * @param offset - the offset to start listing at
     */
    public void setOffset(Integer offset) {
        this.skip = offset;
    }

    /**
     * Mutator for the since variable
     * 
     * @param since - the start place to list commits
     */
    public void setSince(String since) {
        this.since = since;
    }

    /**
     * Mutator for the until variable
     * 
     * @param until - the end place for listing commits
     */
    public void setUntil(String until) {
        this.until = until;
    }

    /**
     * Mutator for the sinceTime variable
     * 
     * @param since - the start place to list commits
     */
    public void setSinceTime(String since) {
        this.sinceTime = since;
    }

    /**
     * Mutator for the untilTime variable
     * 
     * @param until - the end place for listing commits
     */
    public void setUntilTime(String until) {
        this.untilTime = until;
    }

    /**
     * Mutator for the paths variable
     * 
     * @param paths - list of paths to filter commits by
     */
    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    /**
     * Mutator for the page variable
     * 
     * @param page - the page number to build the response
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * Mutator for the elementsPerPage variable
     * 
     * @param elementsPerPage - the number of elements to display in the response per page
     */
    public void setElementsPerPage(int elementsPerPage) {
        this.elementsPerPage = elementsPerPage;
    }

    /**
     * Mutator for the firstParentOnly variable
     * 
     * @param firstParentOnly - true to only show the first parent of a commit
     */
    public void setFirstParentOnly(boolean firstParentOnly) {
        this.firstParentOnly = firstParentOnly;
    }

    /**
     * Mutator for the countChanges variable
     * 
     * @param countChanges - if true, each commit will include a count of each change type compared
     *        to its first parent
     */
    public void setCountChanges(boolean countChanges) {
        this.countChanges = countChanges;
    }

    /**
     * Mutator for the summary variable
     * 
     * @param summary - if true, return all changes from each commit
     */
    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    /**
     * Mutator for the returnRange variable.
     * 
     * @param returnRange - true to only show the first and last commit of the log, as well as a
     *        count of the commits in the range.
     */
    public void setReturnRange(boolean returnRange) {
        this.returnRange = returnRange;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws IllegalArgumentException
     */
    @Override
    protected void runInternal(final CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        LogOp op = geogig.command(LogOp.class).setFirstParentOnly(firstParentOnly);

        if (skip != null) {
            op.setSkip(skip.intValue());
        }
        if (limit != null) {
            op.setLimit(limit.intValue());
        }

        if (this.sinceTime != null || this.untilTime != null) {
            Date since = new Date(0);
            Date until = new Date();
            if (this.sinceTime != null) {
                since = new Date(
                        geogig.command(ParseTimestamp.class).setString(this.sinceTime).call());
            }
            if (this.untilTime != null) {
                until = new Date(
                        geogig.command(ParseTimestamp.class).setString(this.untilTime).call());
            }
            op.setTimeRange(new Range<Date>(Date.class, since, until));
        }

        if (this.since != null) {
            Optional<ObjectId> since;
            since = geogig.command(RevParse.class).setRefSpec(this.since).call();
            Preconditions.checkArgument(since.isPresent(), "Object not found '%s'", this.since);
            op.setSince(since.get());
        }
        if (this.until != null) {
            Optional<ObjectId> until;
            until = geogig.command(RevParse.class).setRefSpec(this.until).call();
            Preconditions.checkArgument(until.isPresent(), "Object not found '%s'", this.until);
            op.setUntil(until.get());
        }

        for (String path : paths) {
            op.addPath(path);
        }

        final Iterator<RevCommit> log = op.call();

        Iterators.advance(log, page * elementsPerPage);

        if (countChanges) {
            final String pathFilter;
            if (!paths.isEmpty()) {
                pathFilter = paths.get(0);
            } else {
                pathFilter = null;
            }
            Function<RevCommit, CommitWithChangeCounts> changeCountFunctor = new Function<RevCommit, CommitWithChangeCounts>() {

                @Override
                public CommitWithChangeCounts apply(RevCommit input) {
                    ObjectId parent = ObjectId.NULL;
                    if (input.getParentIds().size() > 0) {
                        parent = input.getParentIds().get(0);
                    }
                    int added = 0;
                    int modified = 0;
                    int removed = 0;

                    // If it's a shallow clone, the commit may not exist
                    if (parent.equals(ObjectId.NULL) || geogig.objectDatabase().exists(parent)) {
                        try (final AutoCloseableIterator<DiffEntry> diff = geogig
                                .command(DiffOp.class).setOldVersion(parent)
                                .setNewVersion(input.getId()).setFilter(pathFilter).call()) {
                            while (diff.hasNext()) {
                                DiffEntry entry = diff.next();
                                if (entry.changeType() == DiffEntry.ChangeType.ADDED) {
                                    added++;
                                } else if (entry.changeType() == DiffEntry.ChangeType.MODIFIED) {
                                    modified++;
                                } else {
                                    removed++;
                                }
                            }
                        }
                    }

                    return new CommitWithChangeCounts(input, added, modified, removed);
                }
            };

            final Iterator<CommitWithChangeCounts> summarizedLog = Iterators.transform(log,
                    changeCountFunctor);
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeCommitsWithChangeCounts(summarizedLog, elementsPerPage);
                    out.finish();
                }
            });
        } else if (summary) {
            if (paths != null && paths.size() > 0) {
                context.setResponseContent(new LegacyResponse() {

                    @Override
                    public MediaType resolveMediaType(MediaType defaultMediaType) {
                        if (!defaultMediaType.equals(Variants.CSV_MEDIA_TYPE)) {
                            throw new CommandSpecException(
                                    "Unsupported Media Type: This response is only compatible with text/csv.");
                        }
                        return Variants.CSV_MEDIA_TYPE;
                    }

                    @Override
                    public void encode(Writer writer, MediaType format, String baseUrl) {
                        if (!format.equals(Variants.CSV_MEDIA_TYPE)) {
                            throw new CommandSpecException(
                                    "Unsupported Media Type: This response is only compatible with text/csv.");
                        }
                        try {
                            writeCSV(context.getRepository(), writer, log);
                        } catch (Exception e) {
                            Throwables.throwIfUnchecked(e);
                            throw new RuntimeException(e);
                        }

                    }

                    @Override
                    protected void encodeInternal(StreamingWriter writer, MediaType format,
                            String baseUrl) {
                        // Unused
                    }
                });
            } else {
                throw new CommandSpecException(
                        "You must specify a feature type path when getting a summary.");
            }
        } else {
            final boolean rangeLog = returnRange;
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeCommits(log, elementsPerPage, rangeLog);
                    out.finish();
                }
            });
        }

    }

    private void writeCSV(Repository geogig, Writer out, Iterator<RevCommit> log) throws Exception {
        String response = "ChangeType,FeatureId,CommitId,Parent CommitIds,Author Name,Author Email,Author Commit Time,Committer Name,Committer Email,Committer Commit Time,Commit Message";
        out.write(response);
        response = "";
        String path = paths.get(0);
        // This is the feature type object
        Optional<NodeRef> ref = geogig.command(FindTreeChild.class).setChildPath(path)
                .setParent(geogig.workingTree().getTree()).call();
        Optional<RevObject> type = Optional.absent();
        if (ref.isPresent()) {
            type = geogig.command(RevObjectParse.class)
                    .setRefSpec(ref.get().getMetadataId().toString()).call();
        } else {
            throw new CommandSpecException("Couldn't resolve the given path.");
        }
        if (type.isPresent() && type.get() instanceof RevFeatureType) {
            RevFeatureType featureType = (RevFeatureType) type.get();
            Collection<PropertyDescriptor> attribs = featureType.type().getDescriptors();
            int attributeLength = attribs.size();
            for (PropertyDescriptor attrib : attribs) {
                response += "," + escapeCsv(attrib.getName().toString());
            }
            response += '\n';
            out.write(response);
            response = "";
            RevCommit commit = null;

            while (log.hasNext()) {
                commit = log.next();
                String parentId = commit.getParentIds().size() >= 1
                        ? commit.getParentIds().get(0).toString() : ObjectId.NULL.toString();
                try (AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                        .setOldVersion(parentId).setNewVersion(commit.getId().toString())
                        .setFilter(path).call()) {
                    while (diff.hasNext()) {
                        DiffEntry entry = diff.next();
                        response += entry.changeType().toString() + ",";
                        String fid = "";
                        if (entry.newPath() != null) {
                            if (entry.oldPath() != null) {
                                fid = entry.oldPath() + " -> " + entry.newPath();
                            } else {
                                fid = entry.newPath();
                            }
                        } else if (entry.oldPath() != null) {
                            fid = entry.oldPath();
                        }
                        response += fid + ",";
                        response += commit.getId().toString() + ",";
                        response += parentId;
                        if (commit.getParentIds().size() > 1) {
                            for (int index = 1; index < commit.getParentIds().size(); index++) {
                                response += " " + commit.getParentIds().get(index).toString();
                            }
                        }
                        response += ",";
                        if (commit.getAuthor().getName().isPresent()) {
                            response += escapeCsv(commit.getAuthor().getName().get());
                        }
                        response += ",";
                        if (commit.getAuthor().getEmail().isPresent()) {
                            response += escapeCsv(commit.getAuthor().getEmail().get());
                        }
                        response += "," + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
                                .format(new Date(commit.getAuthor().getTimestamp())) + ",";
                        if (commit.getCommitter().getName().isPresent()) {
                            response += escapeCsv(commit.getCommitter().getName().get());
                        }
                        response += ",";
                        if (commit.getCommitter().getEmail().isPresent()) {
                            response += escapeCsv(commit.getCommitter().getEmail().get());
                        }
                        response += "," + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
                                .format(new Date(commit.getCommitter().getTimestamp())) + ",";
                        String message = escapeCsv(commit.getMessage());
                        response += message;
                        if (entry.newObjectId() == ObjectId.NULL) {
                            // Feature was removed so we need to fill out blank attribute values
                            for (int index = 0; index < attributeLength; index++) {
                                response += ",";
                            }
                        } else {
                            // Feature was added or modified so we need to write out the
                            // attribute
                            // values from the feature
                            Optional<RevObject> feature = geogig.command(RevObjectParse.class)
                                    .setObjectId(entry.newObjectId()).call();
                            RevFeature revFeature = (RevFeature) feature.get();
                            for (int index = 0; index < revFeature.size(); index++) {
                                Optional<Object> value = revFeature.get(index);
                                PropertyDescriptor attrib = (PropertyDescriptor) attribs
                                        .toArray()[index];
                                String stringValue = "";
                                if (value.isPresent()) {
                                    FieldType attributeType = FieldType
                                            .forBinding(attrib.getType().getBinding());
                                    switch (attributeType) {
                                    case DATE:
                                        stringValue = new SimpleDateFormat("MM/dd/yyyy z")
                                                .format((java.sql.Date) value.get());
                                        break;
                                    case DATETIME:
                                        stringValue = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
                                                .format((Date) value.get());
                                        break;
                                    case TIME:
                                        stringValue = new SimpleDateFormat("HH:mm:ss z")
                                                .format((Time) value.get());
                                        break;
                                    case TIMESTAMP:
                                        stringValue = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
                                                .format((Timestamp) value.get());
                                        break;
                                    default:
                                        stringValue = escapeCsv(value.get().toString());
                                    }
                                    response += "," + stringValue;
                                } else {
                                    response += ",";
                                }
                            }
                        }
                        response += '\n';
                        out.write(response);
                        response = "";
                    }
                }
            }
        } else {
            // Couldn't resolve FeatureType
            throw new CommandSpecException("Couldn't resolve the given path to a feature type.");
        }
    }

    public class CommitWithChangeCounts {
        private final RevCommit commit;

        private final int adds;

        private final int modifies;

        private final int removes;

        public CommitWithChangeCounts(RevCommit commit, int adds, int modifies, int removes) {
            this.commit = commit;
            this.adds = adds;
            this.modifies = modifies;
            this.removes = removes;
        }

        public RevCommit getCommit() {
            return commit;
        }

        public int getAdds() {
            return adds;
        }

        public int getModifies() {
            return modifies;
        }

        public int getRemoves() {
            return removes;
        }
    }

    private String escapeCsv(String input) {
        String returnStr = input.replace("\"", "\"\"");
        if (input.contains("\"") || input.contains(",") || input.contains("\n")
                || input.contains("\r")) {
            returnStr = "\"" + returnStr + "\"";
        }
        return returnStr;
    }
}
