package org.locationtech.geogig.geotools.data.reader;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.notNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.spatial.ReprojectingFilterVisitor;
import org.geotools.filter.visitor.SpatialFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

public class FeatureReaderBuilder {

    private static final GeometryFactory DEFAULT_GEOMETRY_FACTORY = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    // cache filter factory to avoid the overhead of repeated calls to
    // CommonFactoryFinder.getFilterFactory2
    private static final FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();

    private final Context repo;

    private final SimpleFeatureType fullSchema;

    private String headRef = Ref.HEAD;

    private Filter filter = Filter.INCLUDE;

    private @Nullable String[] outputSchemaPropertyNames = Query.ALL_NAMES;

    private @Nullable ScreenMap screenMap;

    private @Nullable SortBy[] sortBy;

    private @Nullable Integer limit;

    private @Nullable Integer offset;

    private @Nullable String oldHeadRef;

    private ChangeType changeType = ChangeType.ADDED;

    private GeometryFactory geometryFactory = DEFAULT_GEOMETRY_FACTORY;

    public FeatureReaderBuilder(Context repo, SimpleFeatureType fullSchema) {
        this.repo = repo;
        this.fullSchema = fullSchema;
    }

    public static FeatureReaderBuilder builder(Context repo, SimpleFeatureType fullSchema) {
        return new FeatureReaderBuilder(repo, fullSchema);
    }

    public FeatureReaderBuilder oldHeadRef(@Nullable String oldHeadRef) {
        this.oldHeadRef = oldHeadRef;
        return this;
    }

    public FeatureReaderBuilder changeType(ChangeType changeType) {
        checkNotNull(changeType);
        this.changeType = changeType;
        return this;
    }

    public FeatureReaderBuilder headRef(String headRef) {
        checkNotNull(headRef);
        this.headRef = headRef;
        return this;
    }

    public FeatureReaderBuilder propertyNames(@Nullable String... propertyNames) {
        this.outputSchemaPropertyNames = propertyNames;
        return this;
    }

    public FeatureReaderBuilder filter(Filter filter) {
        checkNotNull(filter);
        this.filter = filter;
        return this;
    }

    public FeatureReaderBuilder screenMap(@Nullable ScreenMap screenMap) {
        this.screenMap = screenMap;
        return this;
    }

    public FeatureReaderBuilder geometryFactory(@Nullable GeometryFactory geometryFactory) {
        this.geometryFactory = geometryFactory == null ? DEFAULT_GEOMETRY_FACTORY : geometryFactory;
        return this;
    }

    public FeatureReaderBuilder sortBy(@Nullable SortBy... sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public FeatureReaderBuilder offset(@Nullable Integer offset) {
        this.offset = offset;
        return this;
    }

    public FeatureReaderBuilder limit(@Nullable Integer limit) {
        this.limit = limit;
        return this;
    }

    public FeatureReader<SimpleFeatureType, SimpleFeature> build() {
        final String typeName = fullSchema.getTypeName();

        // query filter in native CRS
        final Filter nativeFilter = resolveNativeFilter();

        // properties needed by the output schema and the in-process filter
        final Set<String> requiredProperties = resolveRequiredProperties(nativeFilter);
        // properties present in the RevTree nodes' extra data
        final Set<String> materializedProperties;
        // whether the RevTree nodes contain all required properties (hence no need to fetch
        // RevFeatures from the database)
        final boolean indexContainsAllRequiredProperties;
        // whether the filter is fully supported by the NodeRef filtering (hence no need for
        // pos-processing filtering)
        final boolean filterIsFullySupported;
        
        final ObjectId featureTypeId = null;

        // the RevTree id at the left side of the diff
        final ObjectId oldFeatureTypeTree;
        // the RevTree id at the right side of the diff
        final ObjectId newFeatureTypeTree;
        // where to get RevTree instances from (either the object or the index database)
        final ObjectStore treeSource;
        {

            // TODO: resolve based on filter, in case the feature type has more than one geometry
            // attribute
            final GeometryDescriptor geometryAttribute = fullSchema.getGeometryDescriptor();
            final Optional<Index> oldHeadIndex;
            final Optional<Index> headIndex;

            final ObjectId oldCanonicalTree = resolveCanonicalTree(oldHeadRef, typeName);
            final ObjectId newCanonicalTree = resolveCanonicalTree(headRef, typeName);

            Optional<Index> indexes[];
            indexes = resolveIndex(oldCanonicalTree, newCanonicalTree, typeName,
                    geometryAttribute.getLocalName());

            oldHeadIndex = indexes[0];
            headIndex = indexes[1];
            // neither is present or both have the same indexinfo
            checkState(!(oldHeadIndex.isPresent() || headIndex.isPresent()) || //
                    headIndex.get().info().equals(oldHeadIndex.get().info()));

            if (oldHeadIndex.isPresent()) {
                oldFeatureTypeTree = oldHeadIndex.get().indexTreeId();
                newFeatureTypeTree = headIndex.get().indexTreeId();
            } else {
                oldFeatureTypeTree = oldCanonicalTree;
                newFeatureTypeTree = newCanonicalTree;
            }

            materializedProperties = resolveMaterializedProperties(headIndex);
            indexContainsAllRequiredProperties = materializedProperties
                    .containsAll(requiredProperties);
            filterIsFullySupported = filterIsFullySupported(nativeFilter,
                    indexContainsAllRequiredProperties);
            treeSource = headIndex.isPresent() ? repo.indexDatabase() : repo.objectDatabase();
        }

        // perform the diff op with the supported Bucket/NodeRef filtering that'll provide the
        // NodeRef iterator to back the FeatureReader with
        DiffTree diffOp = repo.command(DiffTree.class);
        diffOp.setDefaultMetadataId(featureTypeId);
        diffOp.setPreserveIterationOrder(needsPreserveIterationOrder());
        diffOp.setPathFilter(resolveFidFilter(nativeFilter));
        diffOp.setCustomFilter(resolveNodeRefFilter());
        diffOp.setBoundsFilter(resolveBoundsFilter(nativeFilter, newFeatureTypeTree, treeSource));
        diffOp.setChangeTypeFilter(resolveChangeType());
        diffOp.setOldTree(oldFeatureTypeTree);
        diffOp.setNewTree(newFeatureTypeTree);
        diffOp.setLeftSource(treeSource);
        diffOp.setRightSource(treeSource);
        diffOp.recordStats();

        final AutoCloseableIterator<DiffEntry> diffs = diffOp.call();
        AutoCloseableIterator<NodeRef> featureRefs = toFeatureRefs(diffs, changeType);

        // post-processing
        if (filterIsFullySupported) {
            featureRefs = applyOffsetAndLimit(featureRefs);
        }

        final ObjectStore featureSource = repo.objectDatabase();

        AutoCloseableIterator<SimpleFeature> features;

        // contains only the required attributes
        SimpleFeatureType resultSchema;

        BulkFeatureRetriever retriever = new BulkFeatureRetriever(featureSource);
        features = retriever.getGeoToolsFeatures(featureRefs, fullSchema);

        if (!Filter.INCLUDE.equals(nativeFilter)) {
            FilterPredicate filterPredicate = new FilterPredicate(nativeFilter);
            features = AutoCloseableIterator.filter(features, filterPredicate);
        }

        if (!filterIsFullySupported) {
            features = applyOffsetAndLimit(features);
        }

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader;

        featureReader = new FeatureReaderAdapter<SimpleFeatureType, SimpleFeature>(fullSchema,
                features);

        return featureReader;
    }

    private <T> AutoCloseableIterator<T> applyOffsetAndLimit(AutoCloseableIterator<T> iterator) {
        Integer offset = this.offset;
        Integer limit = this.limit;
        if (offset != null) {
            Iterators.advance(iterator, offset.intValue());
        }
        if (limit != null) {
            iterator = AutoCloseableIterator.limit(iterator, limit.intValue());
        }
        return iterator;
    }

    @SuppressWarnings("unchecked")
    private static final Optional<Index>[] NO_INDEX = new Optional[] { absent(), absent() };

    @SuppressWarnings("unchecked")
    private Optional<Index>[] resolveIndex(final ObjectId oldCanonical, final ObjectId newCanonical,
            final String treeName, final String attributeName) {
        if (Boolean.getBoolean("geogig.ignoreindex")) {
            // TODO: remove debugging aid
            System.err.printf(
                    "Ignoring index lookup for %s as indicated by -Dgeogig.ignoreindex=true\n",
                    treeName);
            return NO_INDEX;
        }

        Optional<Index>[] indexes = NO_INDEX;
        final IndexDatabase indexDatabase = repo.indexDatabase();
        Optional<IndexInfo> indexInfo = indexDatabase.getIndex(treeName, attributeName);
        if (indexInfo.isPresent()) {
            IndexInfo info = indexInfo.get();
            Optional<Index> oldIndex = resolveIndex(oldCanonical, info, indexDatabase);
            if (oldIndex.isPresent()) {
                Optional<Index> newIndex = resolveIndex(newCanonical, info, indexDatabase);
                if (newIndex.isPresent()) {
                    indexes = new Optional[2];
                    indexes[0] = oldIndex;
                    indexes[1] = newIndex;
                }
            }
        }
        return indexes;
    }

    private ObjectId resolveCanonicalTree(@Nullable String head, String treeName) {
        if (head == null) {
            return RevTree.EMPTY_TREE_ID;
        }
        String treeishRefSpec = head + ":" + treeName;
        Optional<ObjectId> canonicalTreeId;
        canonicalTreeId = repo.command(ResolveTreeish.class).setTreeish(treeishRefSpec).call();
        return canonicalTreeId.or(RevTree.EMPTY_TREE_ID);
    }

    private Optional<Index> resolveIndex(ObjectId canonicalTreeId, IndexInfo indexInfo,
            IndexDatabase indexDatabase) {

        Index index = new Index(indexInfo, RevTree.EMPTY_TREE_ID, indexDatabase);
        if (!RevTree.EMPTY_TREE_ID.equals(canonicalTreeId)) {
            Optional<ObjectId> indexedTree = indexDatabase.resolveIndexedTree(indexInfo,
                    canonicalTreeId);
            if (indexedTree.isPresent()) {
                index = new Index(indexInfo, indexedTree.get(), indexDatabase);
            }
        }
        return Optional.fromNullable(index);
    }

    private boolean filterIsFullySupported(Filter nativeFilter,
            boolean indexContainsAllRequiredProperties) {

        if (indexContainsAllRequiredProperties) {
            boolean filterSupported = Filter.INCLUDE.equals(nativeFilter) || //
                    nativeFilter instanceof BBOX || //
                    nativeFilter instanceof Id;
            return filterSupported;
        }
        return false;
    }

    private Set<String> resolveMaterializedProperties(Optional<Index> index) {
        Set<String> availableAtts = ImmutableSet.of();
        if (index.isPresent()) {
            IndexInfo info = index.get().info();
            availableAtts = IndexInfo.getMaterializedAttributeNames(info);
        }
        return availableAtts;
    }

    private Set<String> resolveRequiredProperties(Filter nativeFilter) {
        String[] attributeNames = DataUtilities.attributeNames(nativeFilter);
        if (attributeNames == null || attributeNames.length == 0) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(attributeNames);
    }

    private AutoCloseableIterator<NodeRef> toFeatureRefs(
            final AutoCloseableIterator<DiffEntry> diffs, final ChangeType changeType) {

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

    private Filter resolveNativeFilter() {
        Filter nativeFilter = reprojectFilter(this.filter);
        return nativeFilter;
    }

    private Filter reprojectFilter(Filter filter) {
        if (hasSpatialFilter(filter)) {
            CoordinateReferenceSystem crs = fullSchema.getCoordinateReferenceSystem();
            if (crs != null) {
                ReprojectingFilterVisitor visitor;
                visitor = new ReprojectingFilterVisitor(filterFactory, fullSchema);
                filter = (Filter) filter.accept(visitor, null);
            }
        }
        return filter;
    }

    private boolean hasSpatialFilter(Filter filter) {
        SpatialFilterVisitor spatialFilterVisitor = new SpatialFilterVisitor();
        filter.accept(spatialFilterVisitor, null);
        return spatialFilterVisitor.hasSpatialFilter();
    }

    private org.locationtech.geogig.repository.DiffEntry.ChangeType resolveChangeType() {
        switch (changeType) {
        case ADDED:
            return DiffEntry.ChangeType.ADDED;
        case REMOVED:
            return DiffEntry.ChangeType.REMOVED;
        default:
            return DiffEntry.ChangeType.MODIFIED;
        }
    }

    private @Nullable ReferencedEnvelope resolveBoundsFilter(Filter filterInNativeCrs,
            ObjectId featureTypeTreeId, ObjectStore treeSource) {
        if (RevTree.EMPTY_TREE_ID.equals(featureTypeTreeId)) {
            return null;
        }

        CoordinateReferenceSystem crs = fullSchema.getCoordinateReferenceSystem();
        if (crs == null) {
            crs = DefaultEngineeringCRS.GENERIC_2D;
        }
        ReferencedEnvelope queryBounds = new ReferencedEnvelope(crs);
        @SuppressWarnings("unchecked")
        List<ReferencedEnvelope> bounds = (List<ReferencedEnvelope>) filterInNativeCrs
                .accept(new ExtractBounds(crs), null);

        if (bounds != null && !bounds.isEmpty()) {
            final RevTree tree = treeSource.getTree(featureTypeTreeId);
            final ReferencedEnvelope fullBounds = new ReferencedEnvelope(SpatialOps.boundsOf(tree),
                    crs);
            expandToInclude(queryBounds, bounds);

            ReferencedEnvelope clipped = fullBounds.intersection(queryBounds);
            if (clipped.equals(fullBounds)) {
                queryBounds = null;
            }
        }
        return queryBounds;
    }

    private void expandToInclude(ReferencedEnvelope queryBounds, List<ReferencedEnvelope> bounds) {
        for (ReferencedEnvelope e : bounds) {
            queryBounds.expandToInclude(e);
        }
    }

    private Predicate<Bounded> resolveNodeRefFilter() {
        Predicate<Bounded> predicate = Predicates.alwaysTrue();
        if (screenMap != null) {
            predicate = new ScreenMapPredicate(screenMap);
        }
        return predicate;
    }

    private List<String> resolveFidFilter(Filter filter) {
        List<String> pathFilters = ImmutableList.of();
        if (filter instanceof Id) {
            final Set<Identifier> identifiers = ((Id) filter).getIdentifiers();
            Iterator<FeatureId> featureIds = Iterators
                    .filter(Iterators.filter(identifiers.iterator(), FeatureId.class), notNull());
            Preconditions.checkArgument(featureIds.hasNext(), "Empty Id filter");

            pathFilters = Lists.newArrayList(Iterators.transform(featureIds, (fid) -> fid.getID()));
        }

        return pathFilters;
    }

    private boolean needsPreserveIterationOrder() {
        // TODO
        return false;
    }
}
