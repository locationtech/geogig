/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.notNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.ReTypeFeatureReader;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.spatial.ReprojectingFilterVisitor;
import org.geotools.filter.visitor.AbstractFilterVisitor;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.filter.visitor.SpatialFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

/**
 * A builder object for a {@link FeatureReader} that fetches data from a geogig {@link RevTree}
 * representing a "layer".
 * <p>
 * 
 */
public class FeatureReaderBuilder {

    private static final GeometryFactory DEFAULT_GEOMETRY_FACTORY = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    // cache filter factory to avoid the overhead of repeated calls to
    // CommonFactoryFinder.getFilterFactory2
    private static final FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();

    private final Context repo;

    private final RevFeatureType nativeType;

    private final SimpleFeatureType fullSchema;

    private final Set<String> fullSchemaAttributeNames;

    private @Nullable String oldHeadRef;

    private String headRef = Ref.HEAD;

    private Filter filter = Filter.INCLUDE;

    private @Nullable String[] outputSchemaPropertyNames = Query.ALL_NAMES;

    private @Nullable ScreenMap screenMap;

    private @Nullable SortBy[] sortBy;

    private @Nullable Integer limit;

    private @Nullable Integer offset;

    private ChangeType changeType = ChangeType.ADDED;

    private GeometryFactory geometryFactory = DEFAULT_GEOMETRY_FACTORY;

    private NodeRef typeRef;

    private boolean ignoreIndex;

    private boolean retypeIfNeeded = true;

    public FeatureReaderBuilder(Context repo, RevFeatureType nativeType, NodeRef typeRef) {
        this.repo = repo;
        this.nativeType = nativeType;
        this.fullSchema = (SimpleFeatureType) nativeType.type();
        this.typeRef = typeRef;
        this.fullSchemaAttributeNames = Sets.newHashSet(
                Lists.transform(fullSchema.getAttributeDescriptors(), (a) -> a.getLocalName()));
    }

    /**
     * Sets the {@code ignoreIndex} flag to true, indicating that a spatial index lookup will be
     * ignored and only the canonical tree will be used by the returned feature reader.
     * <p>
     * This is particularly useful if the reader is constructed out of a diff between two version of
     * a featuretype tree (e.g. both {@link #oldHeadRef(String)} and {@link #headRef(String)} have
     * been provided), in order for the {@link DiffTree} op used to create the feature stream to
     * accurately represent changes, since most of the time an index reports changes as two separate
     * events for the removal and addition of the feature instead of one single event for the
     * change.
     */
    public FeatureReaderBuilder ignoreIndex() {
        this.ignoreIndex = true;
        return this;
    }

    public static FeatureReaderBuilder builder(Context repo, RevFeatureType nativeType,
            NodeRef typeRef) {
        return new FeatureReaderBuilder(repo, nativeType, typeRef);
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

    /**
     * @param propertyNames which property names to include as the output schema, {@code null} means
     *        all properties
     */
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

        // properties needed by the output schema and the in-process filter, null means all
        // properties, empty list means no-properties needed
        final @Nullable Set<String> requiredProperties = resolveRequiredProperties(nativeFilter);
        // properties present in the RevTree nodes' extra data
        final Set<String> materializedIndexProperties;
        // whether the RevTree nodes contain all required properties (hence no need to fetch
        // RevFeatures from the database)
        final boolean indexContainsAllRequiredProperties;
        // whether the filter is fully supported by the NodeRef filtering (hence no need for
        // pos-processing filtering). This is the case if the filter is a simple BBOX, Id, or
        // INCLUDE, or all the required properties are present in the index Nodes
        final boolean filterIsFullySupportedByIndex;

        final ObjectId featureTypeId = typeRef.getMetadataId();

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

            final Optional<NodeRef> oldCanonicalTree = resolveCanonicalTree(oldHeadRef, typeName);
            final Optional<NodeRef> newCanonicalTree = resolveCanonicalTree(headRef, typeName);
            final ObjectId oldCanonicalTreeId = oldCanonicalTree.isPresent()
                    ? oldCanonicalTree.get().getObjectId() : RevTree.EMPTY_TREE_ID;
            final ObjectId newCanonicalTreeId = newCanonicalTree.isPresent()
                    ? newCanonicalTree.get().getObjectId() : RevTree.EMPTY_TREE_ID;

            // featureTypeId = newCanonicalTree.isPresent() ? newCanonicalTree.get().getMetadataId()
            // : oldCanonicalTree.get().getMetadataId();

            Optional<Index> indexes[];

            // if native filter is a simple "fid filter" then force ignoring the index for a faster
            // look-up (looking up for a fid in the canonical tree is much faster)
            final boolean ignoreIndex = this.ignoreIndex || nativeFilter instanceof Id;
            if (ignoreIndex) {
                indexes = NO_INDEX;
            } else {
                indexes = resolveIndex(oldCanonicalTreeId, newCanonicalTreeId, typeName,
                        geometryAttribute.getLocalName());
            }
            oldHeadIndex = indexes[0];
            headIndex = indexes[1];
            // neither is present or both have the same indexinfo
            checkState(!(oldHeadIndex.isPresent() || headIndex.isPresent()) || //
                    headIndex.get().info().equals(oldHeadIndex.get().info()));

            if (oldHeadIndex.isPresent()) {
                oldFeatureTypeTree = oldHeadIndex.get().indexTreeId();
                newFeatureTypeTree = headIndex.get().indexTreeId();
            } else {
                oldFeatureTypeTree = oldCanonicalTreeId;
                newFeatureTypeTree = newCanonicalTreeId;
            }

            materializedIndexProperties = resolveMaterializedProperties(headIndex);
            indexContainsAllRequiredProperties = materializedIndexProperties
                    .containsAll(requiredProperties);
            filterIsFullySupportedByIndex = filterIsFullySupported(nativeFilter,
                    materializedIndexProperties, requiredProperties);

            treeSource = headIndex.isPresent() ? repo.indexDatabase() : repo.objectDatabase();
        }

        // perform the diff op with the supported Bucket/NodeRef filtering that'll provide the
        // NodeRef iterator to back the FeatureReader with
        DiffTree diffOp = repo.command(DiffTree.class);
        // TODO: for some reason setting the default metadata id is making several tests fail,
        // though it's not really needed here because we have the FeatureType already. Nonetheless
        // this is strange and needs to be revisited.
        diffOp.setDefaultMetadataId(featureTypeId) //
                .setPreserveIterationOrder(shallPreserveIterationOrder())//
                .setPathFilter(createFidFilter(nativeFilter)) //
                .setCustomFilter(createIndexPreFilter(nativeFilter, materializedIndexProperties,
                        filterIsFullySupportedByIndex)) //
                .setBoundsFilter(createBoundsFilter(nativeFilter, newFeatureTypeTree, treeSource)) //
                .setChangeTypeFilter(resolveChangeType()) //
                .setOldTree(oldFeatureTypeTree) //
                .setNewTree(newFeatureTypeTree) //
                .setLeftSource(treeSource) //
                .setRightSource(treeSource) //
                .recordStats();

        AutoCloseableIterator<DiffEntry> diffs;
        diffs = diffOp.call();

        AutoCloseableIterator<NodeRef> featureRefs = toFeatureRefs(diffs, changeType);

        // post-processing
        if (filterIsFullySupportedByIndex) {
            featureRefs = applyOffsetAndLimit(featureRefs);
        }

        final ObjectStore featureSource = repo.objectDatabase();

        AutoCloseableIterator<? extends SimpleFeature> features;

        // contains only the attributes required to satisfy the output schema and the in-process
        // filter
        final SimpleFeatureType resultSchema;

        if (indexContainsAllRequiredProperties) {
            resultSchema = resolveMinimalNativeSchema(requiredProperties);
            CoordinateReferenceSystem nativeCrs = fullSchema.getCoordinateReferenceSystem();
            features = MaterializedIndexFeatureIterator.create(resultSchema, featureRefs,
                    geometryFactory, nativeCrs);
        } else {
            BulkFeatureRetriever retriever = new BulkFeatureRetriever(featureSource);
            // using fullSchema here will build "normal" full-attribute lazy features
            features = retriever.getGeoToolsFeatures(featureRefs, nativeType, geometryFactory);
            resultSchema = fullSchema;
        }

        if (!filterIsFullySupportedByIndex) {
            features = applyPostFilter(nativeFilter, features);
        }

        if (screenMap != null) {
            features = AutoCloseableIterator.transform(features,
                    new ScreenMapGeometryReplacer(screenMap));
        }

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader;
        featureReader = new FeatureReaderAdapter<SimpleFeatureType, SimpleFeature>(resultSchema,
                features);

        // we only want a sub-set of the attributes provided - we need to re-type
        // the features (either from the index or the full-feature)
        final boolean retypeRequired = isRetypeRequired(resultSchema);
        if (retypeRequired) {
            String[] propertyNames = outputSchemaPropertyNames;
            List<String> outputSchemaPropertyNames = propertyNames == null ? Collections.emptyList()
                    : Lists.newArrayList(propertyNames);

            SimpleFeatureType outputSchema;
            outputSchema = SimpleFeatureTypeBuilder.retype(resultSchema, outputSchemaPropertyNames);

            boolean cloneValues = false;
            featureReader = new ReTypeFeatureReader(featureReader, outputSchema, cloneValues);
        }

        return featureReader;
    }

    public FeatureReaderBuilder retypeIfNeeded(boolean retype) {
        this.retypeIfNeeded = retype;
        return this;
    }

    private boolean isRetypeRequired(final SimpleFeatureType resultSchema) {
        if (!retypeIfNeeded) {
            return false;
        }
        final String[] queryProps = outputSchemaPropertyNames;
        if (Query.ALL_NAMES == queryProps) {
            return false;
        }

        List<String> resultNames = Lists.transform(resultSchema.getAttributeDescriptors(),
                (p) -> p.getLocalName());

        boolean retypeRequired = !Arrays.asList(queryProps).equals(resultNames);
        return retypeRequired;
    }

    private AutoCloseableIterator<? extends SimpleFeature> applyPostFilter(Filter nativeFilter,
            AutoCloseableIterator<? extends SimpleFeature> features) {
        PostFilter filterPredicate = new PostFilter(nativeFilter);
        features = AutoCloseableIterator.filter(features, filterPredicate);
        if (screenMap != null) {
            Predicate<SimpleFeature> screenMapFilter = new FeatureScreenMapPredicate(screenMap);
            features = AutoCloseableIterator.filter(features, screenMapFilter);
        }

        features = applyOffsetAndLimit(features);
        return features;
    }

    private SimpleFeatureType resolveMinimalNativeSchema(Set<String> requiredProperties) {
        SimpleFeatureType resultSchema;

        if (requiredProperties.equals(fullSchemaAttributeNames)) {
            resultSchema = fullSchema;
        } else {
            List<String> atts = new ArrayList<>();
            for (AttributeDescriptor d : fullSchema.getAttributeDescriptors()) {
                String attName = d.getLocalName();
                if (requiredProperties.contains(attName)) {
                    atts.add(attName);
                }
            }
            try {
                String[] properties = atts.toArray(new String[requiredProperties.size()]);
                resultSchema = DataUtilities.createSubType(fullSchema, properties);
            } catch (SchemaException e) {
                throw Throwables.propagate(e);
            }
        }

        return resultSchema;
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
        Optional<IndexInfo> indexInfo = indexDatabase.getIndexInfo(treeName, attributeName);
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

    private Optional<NodeRef> resolveCanonicalTree(@Nullable String head, String treeName) {
        Optional<NodeRef> treeRef = Optional.absent();
        if (head != null) {
            Optional<ObjectId> rootTree = repo.command(ResolveTreeish.class).setTreeish(head)
                    .call();
            if (rootTree.isPresent()) {
                RevTree tree = repo.objectDatabase().getTree(rootTree.get());
                treeRef = repo.command(FindTreeChild.class).setParent(tree).setChildPath(treeName)
                        .call();
            }
        }
        return treeRef;
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

    static boolean filterIsFullySupported(Filter nativeFilter, Set<String> materializedProperties,
            Set<String> filterProperties) {

        if (Filter.INCLUDE.equals(nativeFilter) || nativeFilter instanceof BBOX
                || nativeFilter instanceof Id) {
            return true; // simple case
        }

        if (materializedProperties.containsAll(filterProperties)) {
            return true; // this is unlikely to happen, unless geom is in index
        }

        Set<String> missingAttributes = Sets.difference(filterProperties, materializedProperties);

        if (missingAttributes.size() > 1) {
            return false; // 2+ attributes missing. We can possibly handle the geom, but not 2+
        }

        String missingAttribute = missingAttributes.iterator().next(); // there's exactly 1
        VerifySimpleBBoxUsage visitor = new VerifySimpleBBoxUsage(missingAttribute);
        nativeFilter.accept(visitor, null);

        return visitor.isSimple();
    }

    private Set<String> resolveMaterializedProperties(Optional<Index> index) {
        Set<String> availableAtts = ImmutableSet.of();
        if (index.isPresent()) {
            IndexInfo info = index.get().info();
            availableAtts = IndexInfo.getMaterializedAttributeNames(info);
        }
        return availableAtts;
    }

    /**
     * Resolves the properties needed for the output schema, which are mandated both by the
     * properties requested by {@link #propertyNames} and any other property needed to evaluate the
     * {@link #filter} in-process.
     */
    Set<String> resolveRequiredProperties(Filter nativeFilter) {
        if (outputSchemaPropertyNames == Query.ALL_NAMES) {
            return fullSchemaAttributeNames;
        }

        final Set<String> filterAttributes = requiredAttributes(nativeFilter);

        if (outputSchemaPropertyNames.length == 0
                /* Query.NO_NAMES */ && filterAttributes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> requiredProps = Sets.newHashSet(outputSchemaPropertyNames);
        // if the filter is a simple BBOX filter against the default geometry attribute, don't force
        // it, we can optimize bbox filter out of Node.bounds()
        if (!(nativeFilter instanceof BBOX)) {
            requiredProps.addAll(filterAttributes);
        }
        // props required to evaluate the filter in-process
        return requiredProps;
    }

    private Set<String> requiredAttributes(Filter filter) {
        String[] filterAttributes = DataUtilities.attributeNames(filter);
        if (filterAttributes == null || filterAttributes.length == 0) {
            return Collections.emptySet();
        }

        return Sets.newHashSet(filterAttributes);
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
        Filter nativeFilter = this.filter;
        nativeFilter = SimplifyingFilterVisitor.simplify(nativeFilter, fullSchema);
        nativeFilter = reprojectFilter(this.filter);
        return nativeFilter;
    }

    private Filter reprojectFilter(Filter filter) {
        if (hasSpatialFilter(filter)) {
            ReprojectingFilterVisitor visitor;
            visitor = new ReprojectingFilterVisitor(filterFactory, fullSchema);
            filter = (Filter) filter.accept(visitor, null);
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

    private @Nullable ReferencedEnvelope createBoundsFilter(Filter filterInNativeCrs,
            ObjectId featureTypeTreeId, ObjectStore treeSource) {
        if (RevTree.EMPTY_TREE_ID.equals(featureTypeTreeId)) {
            return null;
        }

        CoordinateReferenceSystem nativeCrs = fullSchema.getCoordinateReferenceSystem();
        if (nativeCrs == null) {
            nativeCrs = DefaultEngineeringCRS.GENERIC_2D;
        }

        final Envelope queryBounds = new Envelope();
        List<Envelope> bounds = ExtractBounds.getBounds(filterInNativeCrs);

        if (bounds != null && !bounds.isEmpty()) {
            final RevTree tree = treeSource.getTree(featureTypeTreeId);
            final Envelope fullBounds = SpatialOps.boundsOf(tree);
            expandToInclude(queryBounds, bounds);

            Envelope clipped = fullBounds.intersection(queryBounds);
            if (clipped.equals(fullBounds)) {
                queryBounds.setToNull();
            }
        }
        return queryBounds.isNull() ? null : new ReferencedEnvelope(queryBounds, nativeCrs);
    }

    private void expandToInclude(Envelope queryBounds, List<Envelope> bounds) {
        for (Envelope e : bounds) {
            queryBounds.expandToInclude(e);
        }
    }

    /**
     *
     * @param filter
     * @param materializedProperties
     * @param indexFullySupportsQuery -- if fully supported, use the screenmap
     * @return
     */
    @VisibleForTesting
    Predicate<Bounded> createIndexPreFilter(final Filter filter,
            final Set<String> materializedProperties, boolean indexFullySupportsQuery) {

        Predicate<Bounded> preFilter = new PreFilterBuilder(materializedProperties).build(filter);

        final boolean ignore = Boolean.getBoolean("geogig.ignorescreenmap");
        // if the index is not fully supported, do not apply the screenmap filter at this stage
        // otherwise we will remove too many features
        if (screenMap != null && !ignore && indexFullySupportsQuery) {
            Predicate<Bounded> screenMapFilter = new ScreenMapPredicate(screenMap);
            preFilter = Predicates.and(preFilter, screenMapFilter);
        }
        return preFilter;
    }

    private List<String> createFidFilter(Filter filter) {
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

    /**
     * Determines if the returned iterator shall preserve iteration order among successive calls of
     * the same query.
     * <p>
     * This is the case if:
     * <ul>
     * <li>{@link #limit} and/or {@link #offset} have been set, since most probably the caller is
     * doing paging
     * </ul>
     */
    private boolean shallPreserveIterationOrder() {
        boolean preserveIterationOrder = false;
        preserveIterationOrder |= limit != null || offset != null;
        // TODO: revisit, sortBy should only force iteration order if we can return features in the
        // requested order, otherwise a higher level decorator will still perform the sort
        // in-process so there's no point in forcing the iteration order here
        // preserveIterationOrder |= sortBy != null && sortBy.length > 0;
        return preserveIterationOrder;
    }

    public static class VerifySimpleBBoxUsage extends AbstractFilterVisitor {

        public boolean isUsedInGeometryExpression = false;

        public boolean isUsedInBBoxExpression = false;

        public boolean isUsedInNonBBoxExpression = false;

        public boolean isComplexGeomExpressions = false;

        String propertyName;

        public VerifySimpleBBoxUsage(String propertyName) {
            super();
            this.propertyName = propertyName;
        }

        public boolean isSimple() {
            if (isComplexGeomExpressions || isUsedInNonBBoxExpression)
                return false;

            return isUsedInGeometryExpression && isUsedInBBoxExpression;
        }

        @Override
        protected Object visit(BinarySpatialOperator filter, Object data) {
            String[] attributes = DataUtilities.attributeNames(filter);

            if (attributes.length == 0) {
                return filter; // this should have been optimized away
            }

            if (attributes.length > 1) {
                isComplexGeomExpressions = true; // fail -- to complex
                return filter;
            }

            String attribute = attributes[0];

            if (!attribute.equals(propertyName)) {
                return filter; // not our attribute
            }
            isUsedInGeometryExpression = true;

            if (filter instanceof BBOX) {
                isUsedInBBoxExpression = true;
                return filter;
            }
            // bad
            isUsedInNonBBoxExpression = true;
            return filter;
        }
    }
}
