/* Copyright (c) 2018 Boundless and others.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.ReTypeFeatureReader;
import org.geotools.data.Transaction;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.ContentState;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.geotools.data.reader.FeatureReaderAdapter;
import org.locationtech.geogig.geotools.data.reader.FeatureReaderBuilder;
import org.locationtech.geogig.geotools.data.reader.SpatialDiffMerger;
import org.locationtech.geogig.geotools.data.reader.WalkInfo;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 *
 */
public class GeogigDiffFeatureSource extends ContentFeatureSource {

    private static final Logger log = LoggerFactory.getLogger(GeogigDiffFeatureSource.class);

    private GeoGigDataStore.ChangeType changeType;

    private String oldRoot;

    private boolean flattenSchema;

    private Context oldContext;

    /**
     * <b>Precondition</b>: {@code entry.getDataStore() instanceof GeoGigDataStore}
     */
    public GeogigDiffFeatureSource(ContentEntry entry, String oldRoot) {
        super(entry, Query.ALL);
        checkNotNull(oldRoot);
        this.oldRoot = oldRoot;
        this.oldContext = null;
        checkArgument(entry.getDataStore() instanceof GeoGigDataStore);
    }

    public GeogigDiffFeatureSource(ContentEntry entry, String oldRoot, Context oldContext) {
        super(entry, Query.ALL);
        checkNotNull(oldRoot);
        checkNotNull(oldContext);
        this.oldRoot = oldRoot;
        this.oldContext = oldContext;
        checkArgument(entry.getDataStore() instanceof GeoGigDataStore);
    }

    public void setFlattenSchema(boolean schemaFlattening) {
        getEntry().getState(getTransaction()).setFeatureType(null);
        this.flattenSchema = schemaFlattening;
    }

    public void setChangeType(GeoGigDataStore.ChangeType changeType) {
        this.changeType = changeType;
    }

    String oldRoot() {
        return oldRoot == null ? ObjectId.NULL.toString() : oldRoot;
    }

    GeoGigDataStore.ChangeType changeType() {
        return changeType == null ? ChangeType.ALL : changeType;
    }

    protected @Override SimpleFeatureType buildFeatureType() throws IOException {

        RevFeatureType nativeType = getNativeType();
        SimpleFeatureType featureType = (SimpleFeatureType) nativeType.type();

        final Name assignedName = getEntry().getName();

        final SimpleFeatureType diffFeatureType;
        if (flattenSchema) {
            diffFeatureType = BulkFeatureRetriever.buildFlattenedDiffFeatureType(assignedName,
                    featureType);
        } else {
            diffFeatureType = BulkFeatureRetriever.buildDiffFeatureType(assignedName, featureType);
        }
        return diffFeatureType;
    }

    /**
     * Adds the {@link Hints#FEATURE_DETACHED} hint to the supported hints so the renderer doesn't
     * clone the geometries
     */
    protected @Override void addHints(Set<Hints.Key> hints) {
        hints.add(Hints.FEATURE_DETACHED);
        // if the user turned off the screenmap, then don't advertise it (the renderer will do its
        // own)
        final boolean ignorescreenmap = Boolean.getBoolean("geogig.ignorescreenmap");
        if (!ignorescreenmap)
            hints.add(Hints.SCREENMAP);
        hints.add(Hints.JTS_GEOMETRY_FACTORY);
        // hints.add(Hints.GEOMETRY_SIMPLIFICATION);
    }

    protected @Override boolean canFilter() {
        return true;
    }

    protected @Override boolean canSort() {
        return false;
    }

    /**
     * @return {@code true}
     */
    protected @Override boolean canRetype() {
        return true;
    }

    protected @Override boolean canLimit() {
        return true;
    }

    protected @Override boolean canOffset() {
        return true;
    }

    /**
     * @return {@code false}
     */
    protected @Override boolean canTransact() {
        return false;
    }

    public @Override GeoGigDataStore getDataStore() {
        return (GeoGigDataStore) super.getDataStore();
    }

    public @Override ContentState getState() {
        return super.getState();
    }

    /**
     * Overrides {@link ContentFeatureSource#getName()} to restore back the original meaning of
     * {@link FeatureSource#getName()}
     */
    public @Override Name getName() {
        return getEntry().getName();
    }

    /**
     * Creates a {@link QueryCapabilities} that declares support for
     * {@link QueryCapabilities#isUseProvidedFIDSupported() isUseProvidedFIDSupported}, the
     * datastore supports using the provided feature id in the data insertion workflow as opposed to
     * generating a new id, by looking into the user data map ( {@link Feature#getUserData()}) for a
     * {@link Hints#USE_PROVIDED_FID} key associated to a {@link Boolean#TRUE} value, if the
     * key/value pair is there an attempt to use the provided id will be made, and the operation
     * will fail if the key cannot be parsed into a valid storage identifier.
     */
    protected @Override QueryCapabilities buildQueryCapabilities() {
        return new QueryCapabilities() {
            /**
             * @return {@code true}
             */
            @Override
            public boolean isUseProvidedFIDSupported() {
                return true;
            }

            /**
             * 
             * @return {@code false} by now, will see how/whether we'll support
             *         {@link Query#getVersion()} later
             */
            @Override
            public boolean isVersionSupported() {
                return false;
            }

        };
    }

    protected @Override ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        final Filter filter = (Filter) query.getFilter().accept(new SimplifyingFilterVisitor(),
                null);
        final CoordinateReferenceSystem crs = getSchema().getCoordinateReferenceSystem();
        if (Filter.INCLUDE.equals(filter) && oldRoot == null
                && ChangeType.ADDED.equals(changeType())) {
            NodeRef typeRef = getTypeRef();
            ReferencedEnvelope bounds = new ReferencedEnvelope(crs);
            typeRef.getNode().expand(bounds);
            return bounds;
        }
        if (Filter.EXCLUDE.equals(filter)) {
            return ReferencedEnvelope.create(crs);
        }

        query = new Query(query);
        query.setPropertyNames(Query.NO_NAMES);

        ReferencedEnvelope bounds = new ReferencedEnvelope(crs);
        try (FeatureReader<SimpleFeatureType, SimpleFeature> features = getNativeReader(query,
                false)) {
            while (features.hasNext()) {
                bounds.expandToInclude((ReferencedEnvelope) features.next().getBounds());
            }
        }

        return bounds;
    }

    protected @Override int getCountInternal(Query query) throws IOException {
        final Filter filter = (Filter) query.getFilter().accept(new SimplifyingFilterVisitor(),
                null);
        if (Filter.EXCLUDE.equals(filter)) {
            return 0;
        }

        final Integer offset = query.getStartIndex();
        final Integer maxFeatures = query.getMaxFeatures() == Integer.MAX_VALUE ? null
                : query.getMaxFeatures();

        int size;
        if (Filter.INCLUDE.equals(filter) && oldRoot == null
                && ChangeType.ADDED.equals(changeType())) {
            RevTree tree = getTypeTree();
            size = (int) tree.size();
            if (offset != null) {
                size = size - offset.intValue();
            }
            if (maxFeatures != null) {
                size = Math.min(size, maxFeatures.intValue());
            }
            return size;
        }

        query = new Query(query);
        query.setPropertyNames(Query.NO_NAMES);
        query.setSortBy(null);

        int count = 0;
        try (FeatureReader<SimpleFeatureType, SimpleFeature> features = getNativeReader(query,
                false)) {
            while (features.hasNext()) {
                features.next();
                count++;
            }
        }
        return count;
    }

    protected @Override FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(
            final Query query) throws IOException {

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader;
        featureReader = getNativeReader(query, true);

        // do retyping to satisfy the Query's requested set of properties in the requested order
        final SimpleFeatureType resultSchema = featureReader.getFeatureType();
        final boolean retypeRequired = isRetypeRequired(query, resultSchema);
        if (retypeRequired) {
            String[] propertyNames = query.getPropertyNames();
            List<String> outputSchemaPropertyNames = propertyNames == null ? Collections.emptyList()
                    : Lists.newArrayList(propertyNames);

            SimpleFeatureType outputSchema;
            outputSchema = SimpleFeatureTypeBuilder.retype(resultSchema, outputSchemaPropertyNames);

            boolean cloneValues = false;
            featureReader = new ReTypeFeatureReader(featureReader, outputSchema, cloneValues);
        }
        return featureReader;

    }

    private boolean isRetypeRequired(Query query, SimpleFeatureType resultSchema) {
        final String[] queryProps = query.getPropertyNames();
        if (Query.ALL_NAMES == queryProps) {
            return false;
        }

        // (p) -> p.getLocalName()
        Function<AttributeDescriptor, String> fn =  new Function<AttributeDescriptor, String>() {
            @Override
            public String apply(AttributeDescriptor p) {
                return p.getLocalName();
            }};

        List<String> resultNames = Lists.transform(resultSchema.getAttributeDescriptors(),
               fn);

        boolean retypeRequired = !Arrays.asList(queryProps).equals(resultNames);
        return retypeRequired;
    }

    /**
     * @return a FeatureReader that can fully satisfy the Query's filter and who'se output schema
     *         contains the subset of properties requested by the query's
     *         {@link Query#getProperties() properties} plust any other one needed to evaluate the
     *         filter. Also, the returned attributes are in the native schema's order, not the order
     *         requested by the query.
     */
    private FeatureReader<SimpleFeatureType, SimpleFeature> getNativeReader(final Query query,
            final boolean retypeIfNeeded) throws IOException {

        final Hints hints = query.getHints();
        final @Nullable GeometryFactory geometryFactory = (GeometryFactory) hints
                .get(Hints.JTS_GEOMETRY_FACTORY);
        final @Nullable Integer offset = query.getStartIndex();
        final @Nullable Integer limit = query.isMaxFeaturesUnlimited() ? null
                : query.getMaxFeatures();
        // final @Nullable Double simplifDistance = (Double)
        // hints.get(Hints.GEOMETRY_SIMPLIFICATION);
        final @Nullable ScreenMap screenMap = (ScreenMap) hints.get(Hints.SCREENMAP);
        final @Nullable String[] propertyNames = query.getPropertyNames();
        final Name assignedName = getEntry().getName();

        final Filter filter = query.getFilter();

        final RevFeatureType nativeType = getNativeType();
        final NodeRef typeRef = this.getTypeRef();

        Context leftContext = this.oldContext == null ? getCommandLocator() : this.oldContext;
        Context rightContext = getCommandLocator();
        FeatureReaderBuilder builder = FeatureReaderBuilder.builder(leftContext, rightContext,
                nativeType, typeRef);
        builder//
                .targetSchema(getSchema())//
                .filter(filter)//
                .headRef(getRootRef())//
                .oldHeadRef(oldRoot())//
                .changeType(changeType())//
                .geometryFactory(geometryFactory)//
                .offset(offset)//
                .limit(limit)//
                .screenMap(screenMap)//
                .retypeIfNeeded(false);
        // .ignoreIndex()//
        // .simplificationDistance(simplifDistance)//
        // .propertyNames(propertyNames)//
        // .sortBy(sortBy)//
        // .retypeIfNeeded(retypeIfNeeded)//

        final WalkInfo diffWalkInfo = builder.buildTreeWalk();

        final SimpleFeatureType diffType = getSchema();

        if (diffWalkInfo.screenMapFilter != null) {
            diffWalkInfo.screenMapFilter.collectStats();
        }

        if (diffWalkInfo.diffUsesIndex) {
            SpatialDiffMerger diffMerger = new SpatialDiffMerger();
            diffWalkInfo.diffOp.setConsumerWrapper(diffMerger);
        }
        AutoCloseableIterator<DiffEntry> entries = diffWalkInfo.diffOp.call();
        try {
            BulkFeatureRetriever retriever = new BulkFeatureRetriever(leftContext.objectDatabase(),
                    rightContext.objectDatabase());

            AutoCloseableIterator<SimpleFeature> diffFeatures;
            diffFeatures = retriever.getGeoToolsDiffFeatures(entries, nativeType, diffType,
                    geometryFactory);

            if (screenMap != null) {
                DiffFeatureScreenMapPredicate screenMapPredicate;
                screenMapPredicate = new DiffFeatureScreenMapPredicate(screenMap, diffType);
                diffFeatures = AutoCloseableIterator.filter(diffFeatures, screenMapPredicate);
            }

            if (diffWalkInfo.screenMapFilter != null) {
                diffFeatures = AutoCloseableIterator.fromIterator(diffFeatures, (orig) -> {
                    orig.close();
                    log.info("Pre filter stats: {}", diffWalkInfo.screenMapFilter.stats());
                });
            }

            FeatureReaderAdapter<SimpleFeatureType, SimpleFeature> featureReader;
            featureReader = FeatureReaderAdapter.of(diffType, diffFeatures);

            if (query.getHints().containsKey(GeogigFeatureSource.WALK_INFO_KEY)) {
                GeogigFeatureSource.WALK_INFO.set(diffWalkInfo);
            }

            return featureReader;
        } catch (Exception e) {
            entries.close();
            Throwables.throwIfUnchecked(e);
            throw new IOException(e);
        }
    }

    Context getCommandLocator() {
        Context commandLocator = getDataStore().resolveContext(getTransaction());
        return commandLocator;
    }

    RevFeatureType getNativeType() {

        GeogigContentState state = (GeogigContentState) getEntry().getState(getTransaction());
        RevFeatureType nativeType = state.getNativeType();
        if (nativeType == null) {
            final NodeRef typeRef = getTypeRef();
            final ObjectId metadataId = typeRef.getMetadataId();

            Context context = getCommandLocator();
            nativeType = context.objectDatabase().getFeatureType(metadataId);
            state.setNativeType(nativeType);
        }
        return nativeType;
    }

    String getTypeTreePath() {
        NodeRef typeRef = getTypeRef();
        String path = typeRef.path();
        return path;
    }

    /**
     * @return
     */
    public NodeRef getTypeRef() {
        GeoGigDataStore dataStore = getDataStore();
        Name name = getName();
        Transaction transaction = getTransaction();
        return dataStore.findTypeRef(name, transaction);
    }

    /**
     * @return
     */
    RevTree getTypeTree() {
        String refSpec = getRootRef() + ":" + getTypeTreePath();
        Context commandLocator = getCommandLocator();
        Optional<RevTree> ref = commandLocator.command(RevObjectParse.class).setRefSpec(refSpec)
                .call(RevTree.class);
        Preconditions.checkState(ref.isPresent(), "Ref %s not found on working tree", refSpec);
        return ref.get();
    }

    String getRootRef() {
        GeoGigDataStore dataStore = getDataStore();
        Transaction transaction = getTransaction();
        return dataStore.getRootRef(transaction);
    }

    /**
     * @return
     */
    WorkingTree getWorkingTree() {
        Context commandLocator = getCommandLocator();
        WorkingTree workingTree = commandLocator.workingTree();
        return workingTree;
    }

    Repository getRepository() {
        return getDataStore().getRepository();
    }
}
