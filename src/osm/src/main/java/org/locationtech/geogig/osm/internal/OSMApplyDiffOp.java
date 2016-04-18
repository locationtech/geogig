/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.SubProgressListener;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.osm.internal.coordcache.MapdbPointCache;
import org.locationtech.geogig.osm.internal.coordcache.PointCache;
import org.locationtech.geogig.repository.FeatureToDelete;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.openstreetmap.osmosis.core.OsmosisRuntimeException;
import org.openstreetmap.osmosis.core.container.v0_6.ChangeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.common.ChangeAction;
import org.openstreetmap.osmosis.core.task.v0_6.ChangeSink;
import org.openstreetmap.osmosis.core.util.FixedPrecisionCoordinateConvertor;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlChangeReader;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * Reads a OSM diff file and apply the changes to the current repo.
 * 
 * Changes are filtered to restrict additions to just those new features within the bbox of the
 * current OSM data in the repo, honoring the filter that might have been used to import that
 * preexistent data
 * 
 */

public class OSMApplyDiffOp extends AbstractGeoGigOp<Optional<OSMReport>> {

    private static final PrecisionModel PRECISION_MODEL = new PrecisionModel(
            1D / FixedPrecisionCoordinateConvertor.convertToDouble(1));

    private static final OSMCoordinateSequenceFactory CSFAC = OSMCoordinateSequenceFactory
            .instance();

    private static final GeometryFactory GEOMF = new GeometryFactory(PRECISION_MODEL, 4326, CSFAC);

    // new PackedCoordinateSequenceFactory());
    /**
     * The file to import
     */
    private File file;

    public OSMApplyDiffOp setDiffFile(File file) {
        this.file = file;
        return this;
    }

    @Override
    protected Optional<OSMReport> _call() {
        checkNotNull(file);
        Preconditions.checkArgument(file.exists(), "File does not exist: " + file);

        ProgressListener progressListener = getProgressListener();
        progressListener.setDescription("Applying OSM diff file to GeoGig repo...");

        OSMReport report = parseDiffFileAndInsert();

        return Optional.fromNullable(report);

    }

    public OSMReport parseDiffFileAndInsert() {
        final WorkingTree workTree = workingTree();

        final int queueCapacity = 100 * 1000;
        final int timeout = 1;
        final TimeUnit timeoutUnit = TimeUnit.SECONDS;
        // With this iterator and the osm parsing happening on a separate thread, we follow a
        // producer/consumer approach so that the osm parse thread produces features into the
        // iterator's queue, and WorkingTree.insert consumes them on this thread
        QueueIterator<Feature> target = new QueueIterator<Feature>(queueCapacity, timeout,
                timeoutUnit);

        XmlChangeReader reader = new XmlChangeReader(file, true, resolveCompressionMethod(file));

        ProgressListener progressListener = getProgressListener();
        ConvertAndImportSink sink = new ConvertAndImportSink(target, context, workingTree(),
                platform(), new SubProgressListener(progressListener, 100));
        reader.setChangeSink(sink);

        Thread readerThread = new Thread(reader, "osm-diff-reader-thread");
        readerThread.start();

        // used to set the task status name, but report no progress so it does not interfere
        // with the progress reported by the reader thread
        SubProgressListener noProgressReportingListener = new SubProgressListener(progressListener,
                0) {
            @Override
            public void setProgress(float progress) {
                // no-op
            }
        };

        final Function<Feature, String> parentTreePathResolver = (f) -> f.getType().getName()
                .getLocalPart();

        workTree.insert(parentTreePathResolver, target, noProgressReportingListener, null, null);

        OSMReport report = new OSMReport(sink.getCount(), sink.getNodeCount(), sink.getWayCount(),
                sink.getUnprocessedCount(), sink.getLatestChangeset(), sink.getLatestTimestamp());
        return report;
    }

    private CompressionMethod resolveCompressionMethod(File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".gz")) {
            return CompressionMethod.GZip;
        } else if (fileName.endsWith(".bz2")) {
            return CompressionMethod.BZip2;
        }
        return CompressionMethod.None;
    }

    /**
     * A sink that processes OSM changes and translates the to the repository working tree
     * 
     */
    static class ConvertAndImportSink implements ChangeSink {

        private static final Function<WayNode, Long> NODELIST_TO_ID_LIST = (wn) -> Long
                .valueOf(wn.getNodeId());

        private int count = 0;

        private int nodeCount;

        private int wayCount;

        private int unableToProcessCount = 0;

        private EntityConverter converter = new EntityConverter();

        private long latestChangeset;

        private long latestTimestamp;

        private PointCache pointCache;

        private QueueIterator<Feature> target;

        private ProgressListener progressListener;

        private WorkingTree workTree;

        private Geometry bbox;

        public ConvertAndImportSink(QueueIterator<Feature> target, Context cmdLocator,
                WorkingTree workTree, Platform platform, ProgressListener progressListener) {
            super();
            this.target = target;
            this.workTree = workTree;
            this.progressListener = progressListener;
            this.latestChangeset = 0;
            this.latestTimestamp = 0;
            this.pointCache = new MapdbPointCache(platform);
            Optional<NodeRef> waysNodeRef = cmdLocator.command(FindTreeChild.class)
                    .setChildPath(OSMUtils.WAY_TYPE_NAME).setParent(workTree.getTree()).call();
            Optional<NodeRef> nodesNodeRef = cmdLocator.command(FindTreeChild.class)
                    .setChildPath(OSMUtils.NODE_TYPE_NAME).setParent(workTree.getTree()).call();
            checkArgument(waysNodeRef.isPresent() || nodesNodeRef.isPresent(),
                    "There is no OSM data currently in the repository");
            Envelope envelope = new Envelope();
            if (waysNodeRef.isPresent()) {
                waysNodeRef.get().expand(envelope);
            }
            if (nodesNodeRef.isPresent()) {
                nodesNodeRef.get().expand(envelope);
            }
            bbox = GEOMF.toGeometry(envelope);
        }

        public long getUnprocessedCount() {
            return unableToProcessCount;
        }

        public long getCount() {
            return count;
        }

        public long getNodeCount() {
            return nodeCount;
        }

        public long getWayCount() {
            return wayCount;
        }

        @Override
        public void complete() {
            try {
                progressListener.setProgress(count);
                progressListener.complete();
            } finally {
                try {
                    target.noMoreInput();
                } finally {
                    pointCache.dispose();
                }
            }
        }

        @Override
        public void release() {
            pointCache.dispose();
        }

        @Override
        public void process(ChangeContainer container) {
            if (progressListener.isCanceled()) {
                target.cancel();
                throw new OsmosisRuntimeException("Cancelled by user");
            }
            final EntityContainer entityContainer = container.getEntityContainer();
            final Entity entity = entityContainer.getEntity();
            final ChangeAction changeAction = container.getAction();
            if (changeAction.equals(ChangeAction.Delete)) {
                SimpleFeatureType ft = entity instanceof Node ? OSMUtils.nodeType() : OSMUtils
                        .wayType();
                String id = Long.toString(entity.getId());
                target.put(new FeatureToDelete(ft, id));
                return;
            }
            if (changeAction.equals(ChangeAction.Modify)) {
                // Check that the feature to modify exist. If so, we will just treat it as an
                // addition, overwriting the previous feature
                SimpleFeatureType ft = entity instanceof Node ? OSMUtils.nodeType() : OSMUtils
                        .wayType();
                String path = ft.getName().getLocalPart();
                Optional<org.locationtech.geogig.api.Node> opt = workTree.findUnstaged(path);
                if (!opt.isPresent()) {
                    return;
                }
            }

            if (++count % 10 == 0) {
                progressListener.setProgress(count);
            }
            latestChangeset = Math.max(latestChangeset, entity.getChangesetId());
            latestTimestamp = Math.max(latestTimestamp, entity.getTimestamp().getTime());
            Geometry geom = null;
            switch (entity.getType()) {
            case Node:
                nodeCount++;
                geom = parsePoint((Node) entity);
                break;
            case Way:
                wayCount++;
                geom = parseLine((Way) entity);
                break;
            default:
                return;
            }
            if (geom != null) {
                System.err.printf("%s within %s? %s\n", geom, bbox, geom.within(bbox));
                if (changeAction.equals(ChangeAction.Create) && geom.within(bbox)
                        || changeAction.equals(ChangeAction.Modify)) {
                    Feature feature = converter.toFeature(entity, geom);
                    target.put(feature);
                }
            }
        }

        /**
         * returns the latest timestamp of all the entities processed so far
         * 
         * @return
         */
        public long getLatestTimestamp() {
            return latestTimestamp;
        }

        /**
         * returns the id of the latest changeset of all the entities processed so far
         * 
         * @return
         */
        public long getLatestChangeset() {
            return latestChangeset;
        }

        public boolean hasProcessedEntities() {
            return latestChangeset != 0;
        }

        @Override
        public void initialize(Map<String, Object> map) {
        }

        protected Geometry parsePoint(Node node) {
            double longitude = node.getLongitude();
            double latitude = node.getLatitude();
            OSMCoordinateSequenceFactory csf = CSFAC;
            OSMCoordinateSequence cs = csf.create(1, 2);
            cs.setOrdinate(0, 0, longitude);
            cs.setOrdinate(0, 1, latitude);
            Point pt = GEOMF.createPoint(cs);
            pointCache.put(Long.valueOf(node.getId()), cs);
            return pt;
        }

        /**
         * @return {@code null} if the way nodes cannot be found, or its list of nodes is too short,
         *         the parsed {@link LineString} otherwise
         */
        @Nullable
        protected Geometry parseLine(Way way) {
            final List<WayNode> nodes = way.getWayNodes();

            if (nodes.size() < 2) {
                unableToProcessCount++;
                return null;
            }

            final List<Long> ids = Lists.transform(nodes, NODELIST_TO_ID_LIST);

            try {
                CoordinateSequence coordinates = pointCache.get(ids);
                return GEOMF.createLineString(coordinates);
            } catch (IllegalArgumentException e) {
                unableToProcessCount++;
                return null;
            }

        }
    }

}
