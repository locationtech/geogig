/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata.AUDIT_OP_DELETE;
import static org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata.AUDIT_OP_INSERT;
import static org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata.AUDIT_OP_UPDATE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.geom.GeoPkgGeomReader;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.ConfigGet;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Class for augmenting a geopackage with additional tables to enable smooth import/export
 * functionality with GeoGig. Extra tables keep track of which commit an export came from as well as
 * which features have been modified since the export so that they can be properly merged on import.
 */
public class InterchangeFormat {

    private Context context;

    private ProgressListener progressListener = DefaultProgressListener.NULL;

    private File geopackageDbFile;

    public InterchangeFormat(final File geopackageDbFile, final Context context) {
        checkNotNull(geopackageDbFile);
        checkNotNull(context);
        this.geopackageDbFile = geopackageDbFile;
        this.context = context;
    }

    public InterchangeFormat setProgressListener(ProgressListener progressListener) {
        checkNotNull(progressListener);
        this.progressListener = progressListener;
        return this;
    }

    private void info(String msgFormat, Object... args) {
        progressListener.setDescription(format(msgFormat, args));
    }

    public void createFIDMappingTable(ConcurrentMap<String, String> fidMappings,
            String targetTableName) throws IOException {
        final GeoPackage geopackage = new GeoPackage(geopackageDbFile);
        try {
            final DataSource dataSource = geopackage.getDataSource();

            try (Connection connection = dataSource.getConnection()) {
                try (GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
                    metadata.createFidMappingTable(targetTableName, fidMappings);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } finally {
            geopackage.close();
        }
    }

    /**
     * Creates an audit table for a table in the geopackage. This function requires that the
     * features have already been exported to the geopackage.
     * 
     * @param sourcePathspec path from which features have been exported (supports format
     *        {@code <[<commit-ish>:]<treePath>>}. e.g. {@code buildings}, {@code HEAD~2:buildings},
     *        {@code abc123fg:buildings}, {@code origin/master:buildings} ). {@code buildings}
     *        resolves to {@code HEAD:buildings}.
     * @param targetTableName name of table where features from {@code sourceCommitIsh} have been
     *        exported
     */
    public void export(final String sourcePathspec, final String targetTableName)
            throws IOException {

        final String refspec;
        final String headCommitish;
        final String featureTreePath;
        final ObjectId commitId;

        {
            if (sourcePathspec.contains(":")) {
                refspec = sourcePathspec;
            } else {
                refspec = "HEAD:" + sourcePathspec;
            }

            checkArgument(!refspec.endsWith(":"), "No path specified.");

            String[] split = refspec.split(":");
            headCommitish = split[0];
            featureTreePath = split[1];

            Optional<RevCommit> commit = context.command(RevObjectParse.class)
                    .setRefSpec(headCommitish).call(RevCommit.class);

            checkArgument(commit.isPresent(),
                    "Couldn't resolve '" + refspec + "' to a commitish object");
            commitId = commit.get().getId();
        }

        info("Exporting repository metadata from '%s' (commit %s)...", refspec, commitId);

        final GeoPackage geopackage = new GeoPackage(geopackageDbFile);
        try {
            FeatureEntry featureEntry = geopackage.feature(targetTableName);
            checkState(featureEntry != null, "Table '%s' does not exist", targetTableName);

            try {
                createAuditLog(geopackage, featureTreePath, featureEntry, commitId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        } finally {
            geopackage.close();
        }
    }

    /**
     * Create the audit tables for the specified feature type.
     * 
     * @param geopackage the geopackage to add the tables to
     * @param mappedPath the feature tree path
     * @param fe the feature entry to add audit logs too
     * @param commitId the commit that the exported features came from
     * @throws SQLException
     */
    private void createAuditLog(final GeoPackage geopackage, final String mappedPath,
            final FeatureEntry fe, final ObjectId commitId) throws SQLException {

        info("Creating audit metadata for table '%s'", fe.getIdentifier());

        final DataSource dataSource = geopackage.getDataSource();

        try (Connection connection = dataSource.getConnection();
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
            URI repoURI = context.repository().getLocation();
            metadata.init(repoURI);

            final String auditedTable = fe.getIdentifier();

            metadata.createAudit(auditedTable, mappedPath, commitId);
        }
    }

    public void createChangeLog(final String targetTableName, Map<String, ChangeType> changedNodes)
            throws IOException, SQLException {
        final GeoPackage geopackage = new GeoPackage(geopackageDbFile);
        final DataSource dataSource = geopackage.getDataSource();
        try (Connection connection = dataSource.getConnection();
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
            metadata.createChangeLog(targetTableName);
            metadata.populateChangeLog(targetTableName, changedNodes);
        } finally {
            geopackage.close();
        }

    }

    /**
     * Imports the features from the geopackage based on the existing audit table onto the current
     * branch. If the head commit of the current branch is different from the commit that the
     * features were exported from, the features will be merged into the current branch. The calling
     * function should anticipate the possibility of merge conflicts.
     * 
     * @param commitMessage commit message for the imported features
     * @param authorName author name to use for the commit
     * @param authorEmail author email to use for the commit
     * @param tableNames a list of tables to import from the geopackage, if none are specified, all
     *        tables will be imported
     * @return the commit with the imported features, or the merge commit if it was not a
     *         fast-forward merge
     */
    public GeopkgImportResult importAuditLog(@Nullable String commitMessage,
            @Nullable String authorName, @Nullable String authorEmail,
            @Nullable String... tableNames) {

        final Set<String> importTables = tableNames == null ? ImmutableSet.of()
                : Sets.newHashSet(tableNames);

        List<AuditReport> reports = new ArrayList<>();
        GeoPackage geopackage;
        try {
            geopackage = new GeoPackage(geopackageDbFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final DataSource dataSource = geopackage.getDataSource();

        RevCommit importCommit = null;
        GeopkgImportResult importResult = null;

        try (Connection connection = dataSource.getConnection();
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {

            // Ref::getName, but friendly for Fortify
            Function<AuditTable, String> fn_getTableName = new Function<AuditTable, String>() {
                @Override
                public String apply(AuditTable at) {
                    return at.getTableName();
                }
            };

            // k -> importTables.isEmpty() || importTables.contains(k)
            Predicate<String> fn = new Predicate<String>() {
                @Override
                public boolean apply(String k) {
                    return importTables.isEmpty() || importTables.contains(k);
                }
            };

            final Map<String, AuditTable> tables = Maps
                    .filterKeys(Maps.uniqueIndex(metadata.getAuditTables(), fn_getTableName), fn);

            checkState(tables.size() > 0, "No table to import.");
            Iterator<AuditTable> iter = tables.values().iterator();
            ObjectId commitId = iter.next().getCommitId();
            while (iter.hasNext()) {
                checkState(commitId.equals(iter.next().getCommitId()),
                        "Unable to simultaneously import tables with different source commit ids.");
            }

            RevCommit commit = context.objectDatabase().getCommit(commitId);
            RevTree baseTree = context.objectDatabase().getTree(commit.getTreeId());
            RevTreeBuilder newTreeBuilder = CanonicalTreeBuilder.create(context.objectDatabase(),
                    baseTree);

            Map<String, String> fidMappings = null;
            for (AuditTable t : tables.values()) {
                fidMappings = metadata.getFidMappings(t.getTableName());
                AuditReport report = importAuditLog(geopackage, t, baseTree, newTreeBuilder,
                        fidMappings);
                reports.add(report);
            }

            RevTree newTree = newTreeBuilder.build();
            context.objectDatabase().put(newTree);

            if (authorName == null) {
                authorName = context.command(ConfigGet.class).setName("user.name").call().orNull();
            }
            if (authorEmail == null) {
                authorEmail = context.command(ConfigGet.class).setName("user.email").call()
                        .orNull();
            }

            RevCommitBuilder builder = RevCommit.builder();
            long timestamp = context.platform().currentTimeMillis();

            builder.parentIds(Arrays.asList(commitId));
            builder.treeId(newTree.getId());
            builder.committerTimestamp(timestamp);
            builder.committer(authorName);
            builder.committerEmail(authorEmail);
            builder.authorTimestamp(timestamp);
            builder.author(authorName);
            builder.authorEmail(authorEmail);
            if (commitMessage != null) {
                builder.message(commitMessage);
            } else {
                builder.message("Imported features from geopackage.");
            }

            importCommit = builder.build();
            importResult = new GeopkgImportResult(importCommit);
            for (AuditReport auditReport : reports) {
                if (auditReport.getNewMappings() != null) {
                    importResult.getNewMappings().put(auditReport.table.getFeatureTreePath(),
                            auditReport.getNewMappings());
                }
            }

            context.objectDatabase().put(importCommit);

            MergeOp merge = context.command(MergeOp.class).setAuthor(authorName, authorEmail)
                    .addCommit(importCommit.getId());

            if (commitMessage != null) {
                merge.setMessage("Merge: " + commitMessage);
            }

            MergeReport report = merge.call();
            RevCommit newCommit = report.getMergeCommit();
            importResult.setNewCommit(newCommit);

        } catch (MergeConflictsException e) {
            throw new GeopkgMergeConflictsException(e, importResult);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        } finally {
            geopackage.close();
        }
        return importResult;
    }

    /**
     * Import the specified table and update the tree builder with the updated features.
     * 
     * @param geopackage the geopackage to import from
     * @param auditTable the audit table for the feature type
     * @param baseTree the tree that the features were originally exported from
     * @param newTreeBuilder the tree builder for the updated features
     * @return the audit report for the table
     * @throws SQLException
     */
    private AuditReport importAuditLog(GeoPackage geopackage, AuditTable auditTable,
            RevTree baseTree, RevTreeBuilder newTreeBuilder, Map<String, String> fidMappings)
            throws SQLException {
        info("Importing changes to table %s onto feature tree %s...", auditTable.getTableName(),
                auditTable.getFeatureTreePath());

        AuditReport tableReport = new AuditReport(auditTable);

        try (Connection cx = geopackage.getDataSource().getConnection()) {
            final String sql = format("SELECT * FROM %s", auditTable.getAuditTable());
            try (Statement st = cx.createStatement()) {
                try (ResultSet rs = st.executeQuery(sql)) {

                    final Optional<NodeRef> currentTreeRef = context.command(FindTreeChild.class)
                            .setParent(baseTree).setChildPath(auditTable.getFeatureTreePath())
                            .call();

                    Preconditions.checkState(currentTreeRef.isPresent(),
                            baseTree.toString() + auditTable.getFeatureTreePath());

                    final ObjectStore store = context.objectDatabase();

                    final NodeRef featureTreeRef = currentTreeRef.get();
                    final RevTree currentFeatureTree = store.getTree(featureTreeRef.getObjectId());
                    final RevFeatureType featureType = store
                            .getFeatureType(featureTreeRef.getMetadataId());

                    final Iterator<Change> changes = asChanges(rs, featureType, tableReport);
                    final RevTree newFeatureTree = importAuditLog(store, currentFeatureTree,
                            changes, fidMappings, tableReport);

                    Node featureTreeNode = RevObjectFactory.defaultInstance().createNode(
                            featureTreeRef.name(), newFeatureTree.getId(),
                            featureTreeRef.getMetadataId(), TYPE.TREE,
                            SpatialOps.boundsOf(newFeatureTree), null);

                    newTreeBuilder.put(featureTreeNode);
                }
            }
        }
        return tableReport;
    }

    /**
     * Builds a new feature type tree based on the changes in the audit logs.
     * 
     * @param store the object store
     * @param currentFeatureTree the original feature tree
     * @param changes all of the changes from the audit log
     * @return the newly built tree
     * @throws SQLException
     */
    private RevTree importAuditLog(ObjectStore store, RevTree currentFeatureTree,
            Iterator<Change> changes, Map<String, String> fidMappings, AuditReport report)
            throws SQLException {

        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(store, currentFeatureTree);

        progressListener.setProgress(0);

        Function<Change, RevFeature> function = new Function<InterchangeFormat.Change, RevFeature>() {

            private int count = 0;

            @Override
            public RevFeature apply(Change change) {
                progressListener.setProgress(++count);

                @Nullable
                RevFeature feature = change.getFeature();

                String featureId = null;
                if (fidMappings.containsKey(change.getFeautreId())) {
                    featureId = fidMappings.get(change.getFeautreId());
                } else {
                    featureId = newFeatureId();
                    report.addMapping(change.getFeautreId(), featureId);
                }

                ChangeType type = change.getType();
                switch (type) {
                case REMOVED:
                    builder.remove(featureId);
                    break;
                case ADDED:
                case MODIFIED:
                    Node node = RevObjectFactory.defaultInstance().createNode(featureId,
                            feature.getId(), ObjectId.NULL, TYPE.FEATURE,
                            SpatialOps.boundsOf(feature), null);
                    builder.put(node);
                    return feature;
                default:
                    throw new IllegalStateException();
                }
                return feature;
            }
        };

        Iterator<RevFeature> feautres = Iterators.filter(Iterators.transform(changes, function),
                Predicates.notNull());

        store.putAll(feautres);

        RevTree newTree = builder.build();
        store.put(newTree);
        return newTree;
    }

    /**
     * Converts the audit log into an iterator for all of the changes and updates an audit report
     * with a summary of the changes.
     * 
     * @param rs the rows from the audit log
     * @param featureType the feature type for the features in the table
     * @param report the audit report to update
     * @return
     */
    private Iterator<Change> asChanges(final ResultSet rs, RevFeatureType featureType,
            AuditReport report) {

        return new AbstractIterator<InterchangeFormat.Change>() {

            private final RecordToFeature recordToFeature = new RecordToFeature(
                    (SimpleFeatureType) featureType.type());

            @Override
            protected Change computeNext() {
                try {
                    if (rs.next()) {
                        final String featureId = rs.getString("fid");
                        // final long auditTimestamp = rs.getLong("audit_timestamp");
                        // System.err.println(new Timestamp(auditTimestamp));
                        final int auditOp = rs.getInt("audit_op");
                        final ChangeType changeType = toChangeType(auditOp);

                        RevFeature revFeature = null;
                        if (ChangeType.REMOVED.equals(changeType)) {
                            report.removed.incrementAndGet();
                        } else {
                            revFeature = recordToFeature.apply(rs);
                            if (ChangeType.ADDED.equals(changeType)) {
                                report.added.incrementAndGet();
                            } else {
                                report.changed.incrementAndGet();
                            }
                        }

                        Change change = new Change(featureId, changeType, revFeature);
                        return change;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return endOfData();
            }

            private ChangeType toChangeType(int auditOp) {
                switch (auditOp) {
                case AUDIT_OP_INSERT:
                    return ChangeType.ADDED;
                case AUDIT_OP_UPDATE:
                    return ChangeType.MODIFIED;
                case AUDIT_OP_DELETE:
                    return ChangeType.REMOVED;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Geopackage audit log record contains an invalid audit op code: %d. Expected one if %d(INSERT), %d(UPDATE), %d(DELETE)",
                            auditOp, AUDIT_OP_INSERT, AUDIT_OP_UPDATE, AUDIT_OP_DELETE));
                }
            }
        };
    }

    private String newFeatureId() {
        return SimpleFeatureBuilder.createDefaultFeatureId();
    }

    /**
     * Helper function to convert a row from an audit log into a feature.
     */
    private static class RecordToFeature implements Function<ResultSet, RevFeature> {

        private SimpleFeatureBuilder builder;

        private final List<String> attNames;

        private final String geometryAttribute;

        RecordToFeature(SimpleFeatureType type) {
            this.builder = new SimpleFeatureBuilder(type);
            this.builder.setValidating(false);
            List<AttributeDescriptor> descriptors = type.getAttributeDescriptors();

            // at -> at.getLocalName()
            Function<AttributeDescriptor, String> fn = new Function<AttributeDescriptor, String>() {
                @Override
                public String apply(AttributeDescriptor at) {
                    return at.getLocalName();
                }
            };

            this.attNames = Lists.transform(descriptors, fn);
            GeometryDescriptor geometryDescriptor = type.getGeometryDescriptor();
            this.geometryAttribute = geometryDescriptor == null ? null
                    : geometryDescriptor.getLocalName();
        }

        @Override
        public RevFeature apply(ResultSet rs) {
            builder.reset();
            try {
                for (String attName : attNames) {
                    Object value = rs.getObject(attName);
                    if (attName.equals(geometryAttribute) && value != null) {
                        byte[] bytes = (byte[]) value;
                        value = new GeoPkgGeomReader(bytes).get();
                    }
                    builder.set(attName, value);
                }

                SimpleFeature feature = builder.buildFeature("fakeId");
                return RevFeature.builder().build(feature);
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Helper class for a change from an audit log.
     */
    private static class Change {

        private final String featureId;

        private final ChangeType changeType;

        private final @Nullable RevFeature feature;

        public Change(final String featureId, final ChangeType changeType,
                final @Nullable RevFeature feature) {
            this.featureId = featureId;
            this.changeType = changeType;
            this.feature = feature;

        }

        public ChangeType getType() {
            return changeType;
        }

        public @Nullable RevFeature getFeature() {
            return feature;
        }

        public String getFeautreId() {
            return featureId;
        }

    }
}
