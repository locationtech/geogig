/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterators.filter;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureReader;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.spatial.ReprojectingFilterVisitor;
import org.geotools.filter.visitor.SpatialFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.AutoCloseableIterator;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.vividsolutions.jts.geom.Envelope;

/**
 *
 */
class GeogigFeatureReader<T extends FeatureType, F extends Feature>
        implements FeatureReader<T, F>, Iterator<F> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeogigFeatureReader.class);

    private static final FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();

    private SimpleFeatureType schema;

    private Iterator<SimpleFeature> features;

    private AutoCloseableIterator<DiffEntry> sourceIterator;

    @Nullable
    private Integer offset;

    @Nullable
    private Integer maxFeatures;

    @Nullable
    private final ScreenMapFilter screenMapFilter;

    private Context context;

    /**
     * @param context
     * @param schema
     * @param origFilter
     * @param typeTreePath
     * @param headRef
     * @param oldHeadRef
     * @param offset
     * @param maxFeatures
     * @param changeType
     * @param ignoreAttributes
     */
    public GeogigFeatureReader(final Context context, final SimpleFeatureType schema,
            final Filter origFilter, final String typeTreePath, final String headRef,
            String oldHeadRef, ChangeType changeType, @Nullable Integer offset,
            @Nullable Integer maxFeatures, @Nullable final ScreenMap screenMap,
            final boolean ignoreAttributes) {
        this.context = context;
        checkNotNull(context);
        checkNotNull(schema);
        checkNotNull(origFilter);
        checkNotNull(typeTreePath);
        checkNotNull(headRef);
        checkNotNull(oldHeadRef);
        checkNotNull(changeType);
        this.schema = schema;
        this.offset = offset;
        this.maxFeatures = maxFeatures;

        final String effectiveHead = headRef == null ? Ref.WORK_HEAD : headRef;
        final String effectiveOldHead = oldHeadRef == null ? RevTree.EMPTY_TREE_ID.toString()
                : oldHeadRef;
        final String typeTreeRefSpec = effectiveHead + ":" + typeTreePath;

        final Optional<ObjectId> rootTreeId = context.command(ResolveTreeish.class)
                .setTreeish(effectiveHead).call();

        checkArgument(rootTreeId.isPresent(), "HEAD ref does not resolve to a tree: %s",
                effectiveHead);

        final RevTree rootTree = context.objectDatabase().getTree(rootTreeId.get());

        final Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class)
                .setParent(rootTree).setChildPath(typeTreePath).call();
        checkArgument(typeTreeRef.isPresent(), "Feature type tree not found: %s", typeTreeRefSpec);

        final Filter filter = reprojectFilter(origFilter);

        DiffTree diffOp = context.command(DiffTree.class);
        diffOp.setOldVersion(effectiveOldHead);
        diffOp.setNewVersion(effectiveHead);

        final List<String> pathFilters = resolvePathFilters(typeTreePath, filter);
        diffOp.setPathFilter(pathFilters);

        if (screenMap != null) {
            LOGGER.trace(
                    "Created GeogigFeatureReader with screenMap, assuming it's renderer query");
            this.screenMapFilter = new ScreenMapFilter(screenMap);
            diffOp.setCustomFilter(screenMapFilter);
        } else {
            this.screenMapFilter = null;
            LOGGER.trace("Created GeogigFeatureReader without screenMapFilter");
        }

        ReferencedEnvelope queryBounds = getQueryBounds(filter, typeTreeRef.get());
        if (!queryBounds.isEmpty()) {
            diffOp.setBoundsFilter(queryBounds);
        }
        diffOp.setChangeTypeFilter(changeType(changeType));

        sourceIterator = diffOp.call();
        Iterator<NodeRef> featureRefs = toFeatureRefs(sourceIterator, changeType);

        final boolean filterSupportedByRefs = Filter.INCLUDE.equals(filter)
                || filter instanceof BBOX || filter instanceof Id;

        if (filterSupportedByRefs) {
            featureRefs = applyRefsOffsetLimit(featureRefs);
        }

        // NodeRefToFeature refToFeature = new NodeRefToFeature(context, schema);

        final Function<List<NodeRef>, Iterator<SimpleFeature>> function;
        function = new FetchFunction(context.objectDatabase(), schema);
        final int fetchSize = 1000;
        Iterator<List<NodeRef>> partition = Iterators.partition(featureRefs, fetchSize);
        Iterator<Iterator<SimpleFeature>> transformed = Iterators.transform(partition,
                function);

        // final Iterator<SimpleFeature> featuresUnfiltered = transform(featureRefs,
        // refToFeature);
        final Iterator<SimpleFeature> featuresUnfiltered = Iterators.concat(transformed);

        FilterPredicate filterPredicate = new FilterPredicate(filter);
        Iterator<SimpleFeature> featuresFiltered = filter(featuresUnfiltered, filterPredicate);
        if (!filterSupportedByRefs) {
            featuresFiltered = applyFeaturesOffsetLimit(featuresFiltered);
        }
        this.features = featuresFiltered;
    }

    private DiffEntry.ChangeType changeType(ChangeType changeType) {
        if (changeType == null) {
            return DiffEntry.ChangeType.ADDED;
        }
        switch (changeType) {
        case ADDED:
            return DiffEntry.ChangeType.ADDED;
        case REMOVED:
            return DiffEntry.ChangeType.REMOVED;
        default:
            return DiffEntry.ChangeType.MODIFIED;
        }
    }

    private AutoCloseableIterator<NodeRef> toFeatureRefs(final AutoCloseableIterator<DiffEntry> diffs,
            final ChangeType changeType) {

        return AutoCloseableIterator.transform(diffs, (e) -> {
            if (e.isAdd()) {
                return e.getNewObject();
            }
            if (e.isDelete()) {
                return e.getOldObject();
            }
            return ChangeType.CHANGED_OLD.equals(changeType) ? e.getOldObject() : e.getNewObject();
        });
    }

    private List<String> resolvePathFilters(String typeTreePath, Filter filter) {
        List<String> pathFilters;
        if (filter instanceof Id) {
            final Set<Identifier> identifiers = ((Id) filter).getIdentifiers();
            Iterator<FeatureId> featureIds = filter(filter(identifiers.iterator(), FeatureId.class),
                    notNull());
            Preconditions.checkArgument(featureIds.hasNext(), "Empty Id filter");
            pathFilters = new ArrayList<>();
            while (featureIds.hasNext()) {
                String fid = featureIds.next().getID();
                pathFilters.add(NodeRef.appendChild(typeTreePath, fid));
            }
        } else {
            pathFilters = ImmutableList.of(typeTreePath);
        }
        return pathFilters;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getFeatureType() {
        return (T) schema;
    }

    @Override
    public void close() throws IOException {
        if (sourceIterator != null) {
            sourceIterator.close();
        }
        if (screenMapFilter != null) {
            ScreenMapFilter.Stats stats = screenMapFilter.stats();
            Stopwatch stopwatch = stats.sw.stop();
            // System.err.printf("GeoGigFeatureReader.close(): ScreenMap filtering: %s, time: %s\n",
            // screenMapFilter.stats(), stopwatch);
            LOGGER.debug("GeoGigFeatureReader.close(): ScreenMap filtering: {}, time: {}",
                    screenMapFilter.stats(), stopwatch);
        }
    }

    @Override
    public boolean hasNext() {
        boolean hasNext = features.hasNext();
        return hasNext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public F next() {
        return (F) features.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    private Iterator<SimpleFeature> applyFeaturesOffsetLimit(Iterator<SimpleFeature> features) {
        if (offset != null) {
            Iterators.advance(features, offset.intValue());
        }
        if (maxFeatures != null) {
            features = Iterators.limit(features, maxFeatures.intValue());
        }
        return features;
    }

    private Iterator<NodeRef> applyRefsOffsetLimit(Iterator<NodeRef> featureRefs) {
        if (offset != null) {
            Iterators.advance(featureRefs, offset.intValue());
        }
        if (maxFeatures != null) {
            featureRefs = Iterators.limit(featureRefs, maxFeatures.intValue());
        }
        return featureRefs;
    }

    private class FetchFunction implements Function<List<NodeRef>, Iterator<SimpleFeature>> {

        private class AsFeature implements Function<RevObject, SimpleFeature> {

            private final FeatureBuilder featureBuilder;

            private final ArrayListMultimap<ObjectId, String> fidIndex;

            public AsFeature(FeatureBuilder featureBuilder,
                    ArrayListMultimap<ObjectId, String> fidIndex) {
                this.featureBuilder = featureBuilder;
                this.fidIndex = fidIndex;
            }

            @Override
            public SimpleFeature apply(RevObject obj) {
                final RevFeature revFeature = (RevFeature) obj;
                final ObjectId id = obj.getId();
                List<String> list = fidIndex.get(id);
                final String fid = list.remove(0);

                Feature feature = featureBuilder.build(fid, revFeature);
                return (SimpleFeature) feature;
            }
        }

        private final ObjectDatabase source;

        private final FeatureBuilder featureBuilder;

        // RevObjectParse parser = context.command(RevObjectParse.class);
        public FetchFunction(ObjectDatabase source, SimpleFeatureType schema) {
            this.featureBuilder = new FeatureBuilder(schema);
            this.source = source;
        }

        @Override
        public Iterator<SimpleFeature> apply(List<NodeRef> refs) {
            // Envelope env = new Envelope();
            // List<SimpleFeature> features = new ArrayList<>(refs.size());
            // for(NodeRef ref : refs){
            // env.setToNull();
            // String id = ref.name();
            // Node node = ref.getNode();
            // SimpleFeature feature = (SimpleFeature) featureBuilder.buildLazy(id, node, parser);
            // features.add(feature);
            // }
            // return features.iterator();

            // handle the case where more than one feature has the same hash
            ArrayListMultimap<ObjectId, String> fidIndex = ArrayListMultimap.create();
            for (NodeRef ref : refs) {
                fidIndex.put(ref.getObjectId(), ref.name());
            }
            Iterable<ObjectId> ids = fidIndex.keySet();
            Iterator<RevFeature> all = source.getAll(ids, NOOP_LISTENER, RevFeature.class);

            AsFeature asFeature = new AsFeature(featureBuilder, fidIndex);
            Iterator<SimpleFeature> features = Iterators.transform(all, asFeature);
            return features;
        }

    }

    // private static class NodeRefToFeature implements Function<NodeRef, SimpleFeature> {
    //
    // private RevObjectParse parseRevFeatureCommand;
    //
    // private FeatureBuilder featureBuilder;
    //
    // public NodeRefToFeature(Context commandLocator, SimpleFeatureType schema) {
    // this.featureBuilder = new FeatureBuilder(schema);
    // this.parseRevFeatureCommand = commandLocator.command(RevObjectParse.class);
    // }
    //
    // @Override
    // public SimpleFeature apply(final NodeRef featureRef) {
    // final Node node = featureRef.getNode();
    // final String id = featureRef.name();
    //
    // Feature feature = featureBuilder.buildLazy(id, node, parseRevFeatureCommand);
    // return (SimpleFeature) feature;
    // }
    // };

    private static final class FilterPredicate implements Predicate<SimpleFeature> {
        private Filter filter;

        public FilterPredicate(final Filter filter) {
            this.filter = filter;
        }

        @Override
        public boolean apply(SimpleFeature feature) {
            return filter.evaluate(feature);
        }
    }

    private ReferencedEnvelope getQueryBounds(Filter filterInNativeCrs, NodeRef typeTreeRef) {

        CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
        if (crs == null) {
            crs = DefaultEngineeringCRS.GENERIC_2D;
        }
        ReferencedEnvelope queryBounds = new ReferencedEnvelope(crs);
        @SuppressWarnings("unchecked")
        List<ReferencedEnvelope> bounds = (List<ReferencedEnvelope>) filterInNativeCrs
                .accept(new ExtractBounds(crs), null);
        if (bounds != null && !bounds.isEmpty()) {
            expandToInclude(queryBounds, bounds);

            ReferencedEnvelope fullBounds;
            fullBounds = new ReferencedEnvelope(crs);
            typeTreeRef.expand(fullBounds);

            Envelope clipped = fullBounds.intersection(queryBounds);
            LOGGER.trace("query bounds: {}", queryBounds);
            queryBounds = new ReferencedEnvelope(crs);
            queryBounds.expandToInclude(clipped);
            LOGGER.trace("clipped query bounds: {}", queryBounds);
            if (queryBounds.equals(fullBounds)) {
                queryBounds.setToNull();
            }
        }
        return queryBounds;
    }

    private void expandToInclude(ReferencedEnvelope queryBounds, List<ReferencedEnvelope> bounds) {
        for (ReferencedEnvelope e : bounds) {
            queryBounds.expandToInclude(e);
        }
    }

    /**
     * @param filter
     * @return
     */
    private Filter reprojectFilter(Filter filter) {
        if (hasSpatialFilter(filter)) {
            CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
            if (crs == null) {
                LOGGER.trace(
                        "Not reprojecting filter to native CRS because feature type does not declare a CRS");

            } else {

                filter = (Filter) filter.accept(
                        new ReprojectingFilterVisitor(filterFactory, schema),

                        null);

            }
        }
        return filter;
    }

    private boolean hasSpatialFilter(Filter filter) {
        SpatialFilterVisitor spatialFilterVisitor = new SpatialFilterVisitor();
        filter.accept(spatialFilterVisitor, null);
        return spatialFilterVisitor.hasSpatialFilter();
    }

    private static class ScreenMapFilter implements Predicate<Bounded> {

        static final class Stats {
            private long skippedTrees, skippedBuckets, skippedFeatures;

            private long acceptedTrees, acceptedBuckets, acceptedFeatures;

            private Stopwatch sw = Stopwatch.createStarted();

            void add(final Bounded b, final boolean skip) {
                NodeRef n = b instanceof NodeRef ? (NodeRef) b : null;
                Bucket bucket = b instanceof Bucket ? (Bucket) b : null;
                if (skip) {
                    if (bucket == null) {
                        if (n.getType() == TYPE.FEATURE) {
                            skippedFeatures++;
                        } else {
                            skippedTrees++;
                        }
                    } else {
                        skippedBuckets++;
                    }
                } else {
                    if (bucket == null) {
                        if (n.getType() == TYPE.FEATURE) {
                            acceptedFeatures++;
                        } else {
                            acceptedTrees++;
                        }
                    } else {
                        acceptedBuckets++;
                    }
                }
            }

            @Override
            public String toString() {
                return String.format(
                        "skipped/accepted: Features(%,d/%,d) Buckets(%,d/%,d) Trees(%,d/%,d)",
                        skippedFeatures, acceptedFeatures, skippedBuckets, acceptedBuckets,
                        skippedTrees, acceptedTrees);
            }
        }

        private ScreenMap screenMap;

        private Envelope envelope = new Envelope();

        private Stats stats = new Stats();

        public ScreenMapFilter(ScreenMap screenMap) {
            this.screenMap = screenMap;
        }

        public Stats stats() {
            return stats;
        }

        @Override
        public boolean apply(@Nullable Bounded b) {
            if (b == null) {
                return false;
            }
            envelope.setToNull();
            b.expand(envelope);
            if (envelope.isNull()) {
                return true;
            }
            boolean skip;
            try {
                if (b instanceof NodeRef && ((NodeRef) b).getType() == TYPE.FEATURE) {
                    skip = screenMap.checkAndSet(envelope);
                } else {
                    skip = screenMap.get(envelope);
                }
            } catch (TransformException e) {
                e.printStackTrace();
                return true;
            }
            stats.add(b, skip);
            return !skip;
        }

    }
}
