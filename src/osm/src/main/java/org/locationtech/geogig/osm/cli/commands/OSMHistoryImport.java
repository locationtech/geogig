/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import static org.locationtech.geogig.osm.internal.OSMUtils.NODE_TYPE_NAME;
import static org.locationtech.geogig.osm.internal.OSMUtils.WAY_TYPE_NAME;
import static org.locationtech.geogig.osm.internal.OSMUtils.nodeType;
import static org.locationtech.geogig.osm.internal.OSMUtils.wayType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.management.relation.Relation;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffCount;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.osm.internal.history.Change;
import org.locationtech.geogig.osm.internal.history.Changeset;
import org.locationtech.geogig.osm.internal.history.HistoryDownloader;
import org.locationtech.geogig.osm.internal.history.Node;
import org.locationtech.geogig.osm.internal.history.Primitive;
import org.locationtech.geogig.osm.internal.history.Way;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 *
 */
@Parameters(commandNames = "import-history", commandDescription = "Import OpenStreetmap history")
public class OSMHistoryImport extends AbstractCommand implements CLICommand {

    private static final GeometryFactory GEOMF = new GeometryFactory();

    @ParametersDelegate
    public HistoryImportArgs args = new HistoryImportArgs();

    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(args.numThreads > 0 && args.numThreads < 7,
                "numthreads must be between 1 and 6");

        Console console = cli.getConsole();

        final String osmAPIUrl = resolveAPIURL();

        final long startIndex;
        final long endIndex = args.endIndex;
        if (args.resume) {
            GeoGIG geogig = cli.getGeogig();
            long lastChangeset = getCurrentBranchChangeset(geogig);
            startIndex = 1 + lastChangeset;
        } else {
            startIndex = args.startIndex;
        }
        console.println(String.format("Obtaining OSM changesets %,d to %,d from %s", startIndex,
                args.endIndex, osmAPIUrl));

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                .setNameFormat("osm-history-fetch-thread-%d").build();
        final ExecutorService executor = Executors.newFixedThreadPool(args.numThreads,
                threadFactory);
        final File targetDir = resolveTargetDir(cli.getPlatform());
        console.println("Downloading to " + targetDir.getAbsolutePath());
        console.flush();

        HistoryDownloader downloader;
        downloader = new HistoryDownloader(osmAPIUrl, targetDir, startIndex, endIndex, executor);

        Envelope env = parseBbox();
        Predicate<Changeset> filter = parseFilter(env);
        downloader.setChangesetFilter(filter);
        try {
            importOsmHistory(cli, console, downloader, env);
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new CommandFailedException(e);
            }
        }
    }

    private Predicate<Changeset> parseFilter(Envelope env) {
        if (env == null) {
            return Predicates.alwaysTrue();
        }
        BBoxFiler filter = new BBoxFiler(env);
        return filter;
    }

    private Envelope parseBbox() {
        final String bbox = args.bbox;
        if (bbox != null) {
            String[] split = bbox.split(",");
            checkParameter(split.length == 4,
                    String.format("Invalid bbox format: '%s'. Expected minx,miny,maxx,maxy", bbox));
            try {
                double x1 = Double.parseDouble(split[0]);
                double y1 = Double.parseDouble(split[1]);
                double x2 = Double.parseDouble(split[2]);
                double y2 = Double.parseDouble(split[3]);
                Envelope envelope = new Envelope(x1, x2, y1, y2);
                checkParameter(!envelope.isNull(), "Provided envelope is nil");
                return envelope;
            } catch (NumberFormatException e) {
                String message = String.format(
                        "One or more bbox coordinate can't be parsed to double: '%s'", bbox);
                throw new InvalidParameterException(message, e);
            }
        }
        return null;
    }

    private static class BBoxFiler implements Predicate<Changeset> {

        private Envelope envelope;

        public BBoxFiler(Envelope envelope) {
            this.envelope = envelope;
        }

        @Override
        public boolean apply(Changeset input) {
            Optional<Envelope> wgs84Bounds = input.getWgs84Bounds();
            return wgs84Bounds.isPresent() && envelope.intersects(wgs84Bounds.get());
        }

    }

    private File resolveTargetDir(Platform platform) throws IOException {
        final File targetDir;
        if (args.saveFolder == null) {
            try {
                final File tempDir = platform.getTempDir();
                Preconditions.checkState(tempDir.isDirectory() && tempDir.canWrite());
                File tmp = null;
                for (int i = 0; i < 1000; i++) {
                    tmp = new File(tempDir, "osmchangesets_" + i);
                    if (tmp.mkdir()) {
                        break;
                    }
                    i++;
                }
                targetDir = tmp;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else {
            if (!args.saveFolder.exists() && !args.saveFolder.mkdirs()) {
                throw new IllegalArgumentException("Unable to create directory "
                        + args.saveFolder.getAbsolutePath());
            }
            targetDir = args.saveFolder;
        }
        return targetDir;
    }

    private String resolveAPIURL() {
        String osmAPIUrl;
        if (args.useTestApiEndpoint) {
            osmAPIUrl = HistoryImportArgs.DEVELOPMENT_API_ENDPOINT;
        } else if (args.apiUrl.isEmpty()) {
            osmAPIUrl = HistoryImportArgs.DEFAULT_API_ENDPOINT;
        } else {
            osmAPIUrl = args.apiUrl.get(0);
        }
        return osmAPIUrl;
    }

    private void importOsmHistory(GeogigCLI cli, Console console, HistoryDownloader downloader,
            @Nullable Envelope featureFilter) throws IOException {

        Iterator<Changeset> changesets = downloader.fetchChangesets();

        GeoGIG geogig = cli.getGeogig();
        WorkingTree workingTree = geogig.getContext().workingTree();

        while (changesets.hasNext()) {
            Changeset changeset = changesets.next();
            if (changeset.isOpen()) {
                throw new CommandFailedException("Can't import past changeset " + changeset.getId()
                        + " as it is still open.");
            }
            String desc = String.format("obtaining osm changeset %,d...", changeset.getId());
            console.print(desc);
            console.flush();

            Optional<Iterator<Change>> opchanges = changeset.getChanges().get();
            if (!opchanges.isPresent()) {
                updateBranchChangeset(geogig, changeset.getId());
                console.println(" does not apply.");
                console.flush();
                continue;
            }
            Iterator<Change> changes = opchanges.get();
            console.print("applying...");
            console.flush();

            ObjectId workTreeId = workingTree.getTree().getId();
            long changeCount = insertChanges(cli, changes, featureFilter);
            console.print(String.format("Applied %,d changes, staging...", changeCount));
            console.flush();
            ObjectId afterTreeId = workingTree.getTree().getId();

            DiffObjectCount diffCount = geogig.command(DiffCount.class)
                    .setOldVersion(workTreeId.toString()).setNewVersion(afterTreeId.toString())
                    .call();

            geogig.command(AddOp.class).call();
            console.println(String.format("done. %,d changes actually applied.",
                    diffCount.featureCount()));
            console.flush();

            commit(cli, changeset);
        }
    }

    /**
     * @param cli
     * @param changeset
     * @throws IOException
     */
    private void commit(GeogigCLI cli, Changeset changeset) throws IOException {
        Preconditions.checkArgument(!changeset.isOpen());
        Console console = cli.getConsole();
        console.print("Committing changeset " + changeset.getId() + "...");
        console.flush();

        GeoGIG geogig = cli.getGeogig();
        CommitOp command = geogig.command(CommitOp.class);
        command.setAllowEmpty(true);
        String message = "";
        if (changeset.getComment().isPresent()) {
            message = changeset.getComment().get() + "\nchangeset " + changeset.getId();
        } else {
            message = "changeset " + changeset.getId();
        }
        command.setMessage(message);
        final String userName = changeset.getUserName();
        command.setAuthor(userName, null);
        command.setAuthorTimestamp(changeset.getCreated());
        command.setAuthorTimeZoneOffset(0);// osm timestamps are in GMT

        if (userName != null) {
            command.setCommitter(userName, null);
        }
        command.setCommitterTimestamp(changeset.getClosed().get());
        command.setCommitterTimeZoneOffset(0);// osm timestamps are in GMT

        ProgressListener listener = cli.getProgressListener();
        listener.setProgress(0f);
        listener.started();
        command.setProgressListener(listener);
        try {
            RevCommit commit = command.call();
            Ref head = geogig.command(RefParse.class).setName(Ref.HEAD).call().get();
            Preconditions.checkState(commit.getId().equals(head.getObjectId()));
            updateBranchChangeset(geogig, changeset.getId());
            listener.complete();
            console.println("Commit " + commit.getId().toString());
            console.flush();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * @param geogig
     * @param id
     * @throws IOException
     */
    private void updateBranchChangeset(GeoGIG geogig, long id) throws IOException {
        String path = getBranchChangesetPath(geogig);
        BlobStore blobStore = geogig.getContext().blobStore();
        Blobs.putBlob(blobStore, path, String.valueOf(id));
    }

    private long getCurrentBranchChangeset(GeoGIG geogig) throws IOException {
        String path = getBranchChangesetPath(geogig);

        BlobStore blobStore = geogig.getContext().blobStore();

        Optional<String> blob = Blobs.getBlobAsString(blobStore, path);

        return blob.isPresent() ? Long.parseLong(blob.get()) : 0L;
    }

    private String getBranchChangesetPath(GeoGIG geogig) {
        final String branch = getHead(geogig).getTarget();
        String path = "osm/" + branch;
        return path;
    }

    private SymRef getHead(GeoGIG geogig) {
        final Ref currentHead = geogig.command(RefParse.class).setName(Ref.HEAD).call().get();
        if (!(currentHead instanceof SymRef)) {
            throw new CommandFailedException("Cannot run on a dettached HEAD");
        }
        return (SymRef) currentHead;
    }

    /**
     * @param cli
     * @param changes
     * @param featureFilter
     * @throws IOException
     */
    private long insertChanges(GeogigCLI cli, final Iterator<Change> changes,
            @Nullable Envelope featureFilter) throws IOException {

        final GeoGIG geogig = cli.getGeogig();
        final Repository repository = geogig.getRepository();
        final WorkingTree workTree = repository.workingTree();

        Map<Long, Coordinate> thisChangePointCache = new LinkedHashMap<Long, Coordinate>() {
            /** serialVersionUID */
            private static final long serialVersionUID = 1277795218777240552L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Coordinate> eldest) {
                return size() == 10000;
            }
        };

        long cnt = 0;

        Set<String> deletes = Sets.newHashSet();
        Multimap<String, SimpleFeature> insertsByParent = HashMultimap.create();

        while (changes.hasNext()) {
            Change change = changes.next();
            final String featurePath = featurePath(change);
            if (featurePath == null) {
                continue;// ignores relations
            }
            final String parentPath = NodeRef.parentPath(featurePath);
            if (Change.Type.delete.equals(change.getType())) {
                cnt++;
                deletes.add(featurePath);
            } else {
                final Primitive primitive = change.getNode().isPresent() ? change.getNode().get()
                        : change.getWay().get();
                final Geometry geom = parseGeometry(geogig, primitive, thisChangePointCache);
                if (geom instanceof Point) {
                    thisChangePointCache.put(Long.valueOf(primitive.getId()),
                            ((Point) geom).getCoordinate());
                }

                SimpleFeature feature = toFeature(primitive, geom);

                if (featureFilter == null
                        || featureFilter.intersects((Envelope) feature.getBounds())) {
                    insertsByParent.put(parentPath, feature);
                    cnt++;
                }
            }
        }

        for (String parentPath : insertsByParent.keySet()) {
            Collection<SimpleFeature> features = insertsByParent.get(parentPath);
            if (features.isEmpty()) {
                continue;
            }

            Iterator<? extends Feature> iterator = features.iterator();
            ProgressListener listener = new DefaultProgressListener();
            List<org.locationtech.geogig.api.Node> insertedTarget = null;
            Integer collectionSize = Integer.valueOf(features.size());
            workTree.insert(parentPath, iterator, listener, insertedTarget, collectionSize);
        }
        if (!deletes.isEmpty()) {
            workTree.delete(deletes.iterator());
        }
        return cnt;
    }

    /**
     * @param primitive
     * @param thisChangePointCache
     * @return
     */
    private Geometry parseGeometry(GeoGIG geogig, Primitive primitive,
            Map<Long, Coordinate> thisChangePointCache) {

        if (primitive instanceof Relation) {
            return null;
        }

        if (primitive instanceof Node) {
            Optional<Point> location = ((Node) primitive).getLocation();
            return location.orNull();
        }

        final Way way = (Way) primitive;
        final ImmutableList<Long> nodes = way.getNodes();

        StagingArea index = geogig.getRepository().index();

        FeatureBuilder featureBuilder = new FeatureBuilder(NODE_REV_TYPE);
        List<Coordinate> coordinates = Lists.newArrayList(nodes.size());
        FindTreeChild findTreeChild = geogig.command(FindTreeChild.class);
        ObjectId rootTreeId = geogig.command(ResolveTreeish.class).setTreeish(Ref.HEAD).call()
                .get();
        if (!rootTreeId.isNull()) {
            RevTree headTree = geogig.command(RevObjectParse.class).setObjectId(rootTreeId)
                    .call(RevTree.class).get();
            findTreeChild.setParent(headTree);
        }
        ObjectStore objectDatabase = geogig.getContext().objectDatabase();
        for (Long nodeId : nodes) {
            Coordinate coord = thisChangePointCache.get(nodeId);
            if (coord == null) {
                String fid = String.valueOf(nodeId);
                String path = NodeRef.appendChild(NODE_TYPE_NAME, fid);
                Optional<org.locationtech.geogig.api.Node> ref = index.findStaged(path);
                if (!ref.isPresent()) {
                    Optional<NodeRef> nodeRef = findTreeChild.setChildPath(path).call();
                    if (nodeRef.isPresent()) {
                        ref = Optional.of(nodeRef.get().getNode());
                    } else {
                        ref = Optional.absent();
                    }
                }
                if (ref.isPresent()) {
                    org.locationtech.geogig.api.Node nodeRef = ref.get();

                    RevFeature revFeature = objectDatabase.getFeature(nodeRef.getObjectId());
                    String id = NodeRef.nodeFromPath(nodeRef.getName());
                    Feature feature = featureBuilder.build(id, revFeature);

                    Point p = (Point) ((SimpleFeature) feature).getAttribute("location");
                    if (p != null) {
                        coord = p.getCoordinate();
                        thisChangePointCache.put(Long.valueOf(nodeId), coord);
                    }
                }
            }
            if (coord != null) {
                coordinates.add(coord);
            }
        }
        if (coordinates.size() < 2) {
            return null;
        }
        return GEOMF.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
    }

    /**
     * @param change
     * @return
     */
    private String featurePath(Change change) {
        if (change.getRelation().isPresent()) {
            return null;// ignore relations for the time being
        }
        if (change.getNode().isPresent()) {
            String fid = String.valueOf(change.getNode().get().getId());
            return NodeRef.appendChild(NODE_TYPE_NAME, fid);
        }
        String fid = String.valueOf(change.getWay().get().getId());
        return NodeRef.appendChild(WAY_TYPE_NAME, fid);
    }

    private static final RevFeatureType NODE_REV_TYPE = RevFeatureTypeImpl.build(nodeType());

    private static SimpleFeature toFeature(Primitive feature, Geometry geom) {

        SimpleFeatureType ft = feature instanceof Node ? nodeType() : wayType();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(ft);

        // "visible:Boolean,version:Int,timestamp:long,[location:Point | way:LineString];
        builder.set("visible", Boolean.valueOf(feature.isVisible()));
        builder.set("version", Integer.valueOf(feature.getVersion()));
        builder.set("timestamp", Long.valueOf(feature.getTimestamp()));
        builder.set("changeset", Long.valueOf(feature.getChangesetId()));

        Map<String, String> tags = feature.getTags();
        builder.set("tags", tags);

        String user = feature.getUserName() + ":" + feature.getUserId();
        builder.set("user", user);

        if (feature instanceof Node) {
            builder.set("location", geom);
        } else if (feature instanceof Way) {
            builder.set("way", geom);
            long[] nodes = buildNodesArray(((Way) feature).getNodes());
            builder.set("nodes", nodes);
        } else {
            throw new IllegalArgumentException();
        }

        String fid = String.valueOf(feature.getId());
        SimpleFeature simpleFeature = builder.buildFeature(fid);
        return simpleFeature;
    }

    private static long[] buildNodesArray(List<Long> nodeIds) {
        long[] nodes = new long[nodeIds.size()];
        for (int i = 0; i < nodeIds.size(); i++) {
            nodes[i] = nodeIds.get(i).longValue();
        }
        return nodes;
    }
}
