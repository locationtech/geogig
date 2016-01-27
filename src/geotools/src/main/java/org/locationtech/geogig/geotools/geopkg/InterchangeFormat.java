/* Copyright (c) 2015 Boundless and others.
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
import static org.locationtech.geogig.geotools.geopkg.GeogigMetadata.AUDIT_OP_DELETE;
import static org.locationtech.geogig.geotools.geopkg.GeogigMetadata.AUDIT_OP_INSERT;
import static org.locationtech.geogig.geotools.geopkg.GeogigMetadata.AUDIT_OP_UPDATE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.geom.GeoPkgGeomReader;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

class InterchangeFormat {

    private Repository repository;

    private ProgressListener progressListener = DefaultProgressListener.NULL;

    private File geopackageDbFile;

    public InterchangeFormat(final File geopackageDbFile, final Repository repository) {
        checkNotNull(geopackageDbFile);
        checkNotNull(repository);
        this.geopackageDbFile = geopackageDbFile;
        this.repository = repository;
    }

    public InterchangeFormat setProgressListener(ProgressListener progressListener) {
        checkNotNull(progressListener);
        this.progressListener = progressListener;
        return this;
    }

    private void info(String msgFormat, Object... args) {
        progressListener.setDescription(format(msgFormat, args));
    }

    /**
     * @param sourceTreeIsh tree-ish from which features have been exported (supports format
     *        {@code <[<tree-ish>:]<treePath>>}. e.g. {@code buildings}, {@code HEAD~2:buildings},
     *        {@code abc123fg:buildings}, {@code origin/master:buildings} ). {@code buildings}
     *        resolves to {@code WORK_HEAD:buildings}.
     * @param targetTableName name of table where features from {@code sourceTreeIsh} have been
     *        exported
     */
    public void export(final String sourceTreeIsh, final String targetTableName) throws IOException {

        final String refspec;
        final String headTreeish;
        final String featureTreePath;
        final ObjectId rootTreeId;

        {
            if (sourceTreeIsh.contains(":")) {
                refspec = sourceTreeIsh;
            } else {
                refspec = "WORK_HEAD:" + sourceTreeIsh;
            }

            checkArgument(!refspec.endsWith(":"), "No path specified.");

            String[] split = refspec.split(":");
            headTreeish = split[0];
            featureTreePath = split[1];
            Optional<ObjectId> rootId = repository.command(ResolveTreeish.class)
                    .setTreeish(headTreeish).call();

            checkArgument(rootId.isPresent(), "Couldn't resolve '" + refspec
                    + "' to a treeish object");
            rootTreeId = rootId.get();
        }

        info("Exporting repository metadata from '%s' (tree %s)...", refspec, rootTreeId);

        final GeoPackage geopackage = new GeoPackage(geopackageDbFile);
        try {
            FeatureEntry featureEntry = geopackage.feature(targetTableName);
            checkState(featureEntry != null, "Table '%s' does not exist", targetTableName);

            try {
                createAuditLog(geopackage, featureTreePath, featureEntry, rootTreeId);
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            }

        } finally {
            geopackage.close();
        }
    }

    private void createAuditLog(final GeoPackage geopackage, final String mappedPath,
            final FeatureEntry fe, final ObjectId rootTreeId) throws SQLException {

        info("Creating audit metadata for table '%s'", fe.getIdentifier());

        final DataSource dataSource = geopackage.getDataSource();

        try (Connection connection = dataSource.getConnection()) {
            GeogigMetadata metadata = new GeogigMetadata(connection);
            URI repoURI = repository.getLocation();
            metadata.init(repoURI);

            final String auditedTable = fe.getIdentifier();

            metadata.createAudit(auditedTable, mappedPath, rootTreeId);
        }
    }

    public List<AuditReport> importAuditLog(@Nullable String... tableNames) {

        final Set<String> importTables = tableNames == null ? ImmutableSet.of() : Sets
                .newHashSet(tableNames);

        List<AuditReport> reports = new ArrayList<>();
        GeoPackage geopackage;
        try {
            geopackage = new GeoPackage(geopackageDbFile);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        final DataSource dataSource = geopackage.getDataSource();

        try (Connection connection = dataSource.getConnection()) {
            GeogigMetadata metadata = new GeogigMetadata(connection);

            final Map<String, AuditTable> tables = Maps.filterKeys(
                    Maps.uniqueIndex(metadata.getAuditTables(), t -> t.getTableName()),
                    k -> importTables.isEmpty() || importTables.contains(k));

            for (AuditTable t : tables.values()) {
                AuditReport report = importAuditLog(geopackage, t);
                reports.add(report);
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            geopackage.close();
        }
        return reports;
    }

    private AuditReport importAuditLog(GeoPackage geopackage, AuditTable auditTable)
            throws SQLException {
        info("Importing changes to table %s onto feature tree %s...", auditTable.getTableName(),
                auditTable.getFeatureTreePath());

        AuditReport tableReport = new AuditReport(auditTable);

        try (Connection cx = geopackage.getDataSource().getConnection()) {
            final String sql = format("SELECT * FROM %s", auditTable.getAuditTable());
            try (Statement st = cx.createStatement()) {
                try (ResultSet rs = st.executeQuery(sql)) {

                    final RevTree worktree = repository.workingTree().getTree();

                    final Optional<NodeRef> currentTreeRef = repository
                            .command(FindTreeChild.class).setParent(worktree)
                            .setChildPath(auditTable.getFeatureTreePath()).call();

                    Preconditions.checkState(currentTreeRef.isPresent());

                    final ObjectStore store = repository.objectDatabase();

                    final NodeRef featureTreeRef = currentTreeRef.get();
                    final RevTree currentFeatureTree = store.getTree(featureTreeRef.getObjectId());
                    final RevFeatureType featureType = store.getFeatureType(featureTreeRef
                            .getMetadataId());

                    final Iterator<Change> changes = asChanges(rs, featureType, tableReport);
                    final RevTree newFeatureTree = importAuditLog(store, currentFeatureTree,
                            changes);

                    RevTreeBuilder workTreeBuilder = new RevTreeBuilder(store, worktree);

                    Node featureTreeNode = Node.create(featureTreeRef.name(),
                            newFeatureTree.getId(), featureTreeRef.getMetadataId(), TYPE.TREE,
                            SpatialOps.boundsOf(newFeatureTree));

                    workTreeBuilder.put(featureTreeNode);
                    RevTree newWorkTree = workTreeBuilder.build();
                    store.put(newWorkTree);
                    repository.workingTree().updateWorkHead(newWorkTree.getId());
                }
            }
        }
        return tableReport;
    }

    private RevTree importAuditLog(ObjectStore store, RevTree currentFeatureTree,
            Iterator<Change> changes) throws SQLException {

        RevTreeBuilder builder = new RevTreeBuilder(store, currentFeatureTree);

        progressListener.setProgress(0);

        Function<Change, RevFeature> function = new Function<InterchangeFormat.Change, RevFeature>() {

            private int count = 0;

            @Override
            public RevFeature apply(Change change) {
                progressListener.setProgress(++count);

                @Nullable
                RevFeature feature = change.getFeature();

                String feautreId = change.getFeautreId();

                ChangeType type = change.getType();
                switch (type) {
                case REMOVED:
                    builder.remove(feautreId);
                    break;
                case ADDED:
                case MODIFIED:
                    Node node = Node.create(feautreId, feature.getId(), ObjectId.NULL,
                            TYPE.FEATURE, SpatialOps.boundsOf(feature));
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
                    throw Throwables.propagate(e);
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
                    throw new IllegalArgumentException(
                            String.format(
                                    "Geopackage audit log record contains an invalid audit op code: %d. Expected one if %d(INSERT), %d(UPDATE), %d(DELETE)",
                                    auditOp, AUDIT_OP_INSERT, AUDIT_OP_UPDATE, AUDIT_OP_DELETE));
                }
            }
        };
    }

    private static class RecordToFeature implements Function<ResultSet, RevFeature> {

        private SimpleFeatureBuilder builder;

        private final List<String> attNames;

        private final String geometryAttribute;

        RecordToFeature(SimpleFeatureType type) {
            this.builder = new SimpleFeatureBuilder(type);
            this.builder.setValidating(false);
            List<AttributeDescriptor> descriptors = type.getAttributeDescriptors();
            this.attNames = Lists.transform(descriptors, at -> at.getLocalName());
            GeometryDescriptor geometryDescriptor = type.getGeometryDescriptor();
            this.geometryAttribute = geometryDescriptor == null ? null : geometryDescriptor
                    .getLocalName();
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
                return RevFeatureBuilder.build(feature);
            } catch (SQLException | IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

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
