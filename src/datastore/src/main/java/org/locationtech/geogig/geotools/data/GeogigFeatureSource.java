/* Copyright (c) 2013-2016 Boundless and others.
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
import org.locationtech.geogig.geotools.data.reader.FeatureReaderBuilder;
import org.locationtech.geogig.geotools.data.reader.WalkInfo;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 *
 */
public class GeogigFeatureSource extends ContentFeatureSource {

    private final GeogigFeatureVisitorHandler visitorHandler = new GeogigFeatureVisitorHandler();

    private static final String SCREENMAP_REPLACE_GEOMETRY_WITH_PX = "Renderer.ScreenMap.replaceGeometryWithPX";

    private static final Hints.ConfigurationMetadataKey SCREENMAP_REPLACE_GEOMETRY_WITH_PX_KEY = Hints.ConfigurationMetadataKey
            .get(SCREENMAP_REPLACE_GEOMETRY_WITH_PX);

    public static final Hints.Key WALK_INFO_KEY = new Hints.Key(Boolean.class);

    public static final ThreadLocal<WalkInfo> WALK_INFO = new ThreadLocal<>();

    /**
     * <b>Precondition</b>: {@code entry.getDataStore() instanceof GeoGigDataStore}
     * 
     * @param entry
     */
    public GeogigFeatureSource(ContentEntry entry) {
        super(entry, Query.ALL);
        checkArgument(entry.getDataStore() instanceof GeoGigDataStore);
    }

    /**
     * Adds the {@link Hints#FEATURE_DETACHED} hint to the supported hints so the renderer doesn't
     * clone the geometries
     */
    @Override
    protected void addHints(Set<Hints.Key> hints) {
        hints.add(Hints.FEATURE_DETACHED);
        // if the user turned off the screenmap, then don't advertise it (the renderer will do its
        // own)
        final boolean ignorescreenmap = Boolean.getBoolean("geogig.ignorescreenmap");
        if (!ignorescreenmap)
            hints.add(Hints.SCREENMAP);
        hints.add(Hints.JTS_GEOMETRY_FACTORY);
        hints.add(Hints.GEOMETRY_SIMPLIFICATION);
        hints.add(SCREENMAP_REPLACE_GEOMETRY_WITH_PX_KEY);
    }

    @Override
    protected boolean canFilter() {
        return true;
    }

    @Override
    protected boolean canSort() {
        return false;
    }

    /**
     * @return {@code true}
     */
    @Override
    protected boolean canRetype() {
        return true;
    }

    @Override
    protected boolean canLimit() {
        return true;
    }

    @Override
    protected boolean canOffset() {
        return true;
    }

    /**
     * @return {@code true}
     */
    @Override
    protected boolean canTransact() {
        return true;
    }

    @Override
    public GeoGigDataStore getDataStore() {
        return (GeoGigDataStore) super.getDataStore();
    }

    @Override
    public ContentState getState() {
        return super.getState();
    }

    /**
     * Overrides {@link ContentFeatureSource#getName()} to restore back the original meaning of
     * {@link FeatureSource#getName()}
     */
    @Override
    public Name getName() {
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
    @Override
    protected QueryCapabilities buildQueryCapabilities() {
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

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        final Filter filter = (Filter) query.getFilter().accept(new SimplifyingFilterVisitor(),
                null);
        final CoordinateReferenceSystem crs = getSchema().getCoordinateReferenceSystem();
        if (Filter.INCLUDE.equals(filter)) {
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

    @Override
    protected int getCountInternal(Query query) throws IOException {
        final Filter filter = (Filter) query.getFilter().accept(new SimplifyingFilterVisitor(),
                null);
        if (Filter.EXCLUDE.equals(filter)) {
            return 0;
        }

        final Integer offset = query.getStartIndex();
        final Integer maxFeatures = query.getMaxFeatures() == Integer.MAX_VALUE ? null
                : query.getMaxFeatures();

        int size;
        if (Filter.INCLUDE.equals(filter)) {
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

    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(final Query query)
            throws IOException {

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

    public @VisibleForTesting @Override boolean handleVisitor(Query query, FeatureVisitor visitor) {
        return visitorHandler.handle(visitor, query, this);
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

        final Context context = getCommandLocator();

        final Hints hints = query.getHints();
        final @Nullable GeometryFactory geometryFactory = (GeometryFactory) hints
                .get(Hints.JTS_GEOMETRY_FACTORY);
        final @Nullable Integer offset = query.getStartIndex();
        final @Nullable Integer limit = query.isMaxFeaturesUnlimited() ? null
                : query.getMaxFeatures();
        final @Nullable Double simplifyDistance = (Double) hints.get(Hints.GEOMETRY_SIMPLIFICATION);
        final @Nullable Boolean replaceScreenGeomWithPX = (Boolean) hints
                .get(SCREENMAP_REPLACE_GEOMETRY_WITH_PX_KEY);

        final @Nullable ScreenMap screenMap = (ScreenMap) hints.get(Hints.SCREENMAP);
        final @Nullable String[] propertyNames = query.getPropertyNames();
        final @Nullable SortBy[] sortBy = query.getSortBy();
        // final Name assignedName = getEntry().getName();

        final Filter filter = query.getFilter();

        final RevFeatureType nativeType = getNativeType();
        final NodeRef typeRef = this.getTypeRef();

        FeatureReaderBuilder builder = FeatureReaderBuilder.builder(context, nativeType, typeRef);

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = builder//
                .targetSchema(getSchema())//
                .filter(filter)//
                .headRef(getRootRef())//
                // .oldHeadRef(oldRoot())//
                // .changeType(changeType())//
                .geometryFactory(geometryFactory)//
                .simplificationDistance(simplifyDistance)//
                .screenMapReplaceGeometryWithPx(replaceScreenGeomWithPX)//
                .offset(offset)//
                .limit(limit)//
                .propertyNames(propertyNames)//
                .screenMap(screenMap)//
                .sortBy(sortBy)//
                .retypeIfNeeded(retypeIfNeeded)//
                .build();

        if (query.getHints().containsKey(GeogigFeatureSource.WALK_INFO_KEY)) {
            WALK_INFO.set(builder.getBuiltWalkInfo());
        }
        return featureReader;

    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {

        RevFeatureType nativeType = getNativeType();
        SimpleFeatureType featureType = (SimpleFeatureType) nativeType.type();

        final Name name = featureType.getName();
        final Name assignedName = getEntry().getName();

        if (assignedName.getNamespaceURI() != null && !assignedName.equals(name)) {
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.init(featureType);
            builder.setName(assignedName);
            featureType = builder.buildFeatureType();
        }
        return featureType;
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
