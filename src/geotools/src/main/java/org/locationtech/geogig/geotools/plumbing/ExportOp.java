/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.collection.BaseFeatureCollection;
import org.geotools.feature.collection.DelegateFeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;

/**
 * Internal operation for creating a FeatureCollection from a tree content.
 * 
 */
@Hookable(name = "export")
public class ExportOp extends AbstractGeoGigOp<SimpleFeatureStore> {

    private static final Function<Feature, Optional<Feature>> IDENTITY = (feature) -> Optional
            .fromNullable(feature);

    private String path;

    private Supplier<SimpleFeatureStore> targetStoreProvider;

    private Function<Feature, Optional<Feature>> function = IDENTITY;

    private ObjectId filterFeatureTypeId;

    private boolean forceExportDefaultFeatureType;

    private boolean alter;

    private boolean transactional;

    private ReferencedEnvelope bboxFilter;

    /**
     * Constructs a new export operation.
     */
    public ExportOp() {
        this.transactional = true;
    }

    /**
     * Executes the export operation using the parameters that have been specified.
     * 
     * @return a FeatureCollection with the specified features
     */
    @Override
    protected SimpleFeatureStore _call() {
        final ObjectDatabase database = objectDatabase();
        if (filterFeatureTypeId != null) {
            RevObject filterType = database.getIfPresent(filterFeatureTypeId);
            checkArgument(filterType instanceof RevFeatureType,
                    "Provided filter feature type is does not exist");
        }

        final SimpleFeatureStore targetStore = getTargetStore();

        final String refspec = resolveRefSpec();
        final String treePath = refspec.substring(refspec.indexOf(':') + 1);
        final RevTree rootTree = resolveRootTree(refspec);
        final NodeRef typeTreeRef = resolTypeTreeRef(refspec, treePath, rootTree);

        final ObjectId defaultMetadataId = typeTreeRef.getMetadataId();

        final RevTree typeTree = database.getTree(typeTreeRef.getObjectId());

        final ProgressListener progressListener = getProgressListener();

        progressListener.started();
        progressListener.setDescription(
                "Exporting from " + path + " to " + targetStore.getName().getLocalPart() + "... ");

        final ReferencedEnvelope bboxFilter = this.bboxFilter;
        final Iterator<SimpleFeature> filtered;
        {
            final Iterator<SimpleFeature> plainFeatures = getFeatures(typeTree, database,
                    defaultMetadataId, bboxFilter, progressListener);

            Iterator<SimpleFeature> adaptedFeatures = adaptToArguments(plainFeatures,
                    defaultMetadataId);

            Iterator<Optional<Feature>> transformed = Iterators.transform(adaptedFeatures,
                    ExportOp.this.function);

            // (f) -> (SimpleFeature) f.orNull()
            Function<Optional<Feature>, SimpleFeature> fn =  new Function<Optional<Feature>, SimpleFeature>() {
                @Override
                public SimpleFeature apply(Optional<Feature> f) {
                    return (SimpleFeature) f.orNull();
                }};

            Iterator<SimpleFeature> filteredIter = Iterators.filter(
                    Iterators.transform(transformed, fn),
                    Predicates.notNull());

            // check the resulting schema has something to contribute
            PeekingIterator<SimpleFeature> peekingIt = Iterators.peekingIterator(filteredIter);
            if (peekingIt.hasNext()) {
                Function<AttributeDescriptor, String> toString = (at) -> at.getLocalName();
                SimpleFeature peek = peekingIt.peek();
                Set<String> sourceAtts = new HashSet<String>(
                        Lists.transform(peek.getFeatureType().getAttributeDescriptors(), toString));
                Set<String> targetAtts = new HashSet<String>(Lists
                        .transform(targetStore.getSchema().getAttributeDescriptors(), toString));
                if (Sets.intersection(sourceAtts, targetAtts).isEmpty()) {
                    throw new GeoToolsOpException(StatusCode.UNABLE_TO_ADD,
                            "No common attributes between source and target feature types");
                }
            }

            filtered = peekingIt;
        }
        FeatureCollection<SimpleFeatureType, SimpleFeature> asFeatureCollection = new BaseFeatureCollection<SimpleFeatureType, SimpleFeature>() {

            @Override
            public FeatureIterator<SimpleFeature> features() {

                return new DelegateFeatureIterator<SimpleFeature>(filtered);
            }
        };

        // add the feature collection to the feature store
        final Transaction transaction;
        if (transactional) {
            transaction = new DefaultTransaction("create");
        } else {
            transaction = Transaction.AUTO_COMMIT;
        }
        try {
            targetStore.setTransaction(transaction);
            try {
                targetStore.addFeatures(asFeatureCollection);
                // ArrayList al = new ArrayList();
                // while (filtered.hasNext())
                // al.add(filtered.next());
                // FeatureReader<SimpleFeatureType, SimpleFeature> reader = new
                // IteratorBackedFeatureReader(null,filtered );
                // targetStore.setFeatures(reader);
                transaction.commit();
            } catch (final Exception e) {
                if (transactional) {
                    transaction.rollback();
                }
                Throwables.throwIfInstanceOf(e, GeoToolsOpException.class);
                throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_ADD);
            } finally {
                transaction.close();
            }
        } catch (IOException e) {
            throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_ADD);
        }

        progressListener.complete();

        return targetStore;

    }

    private static Iterator<SimpleFeature> getFeatures(final RevTree typeTree,
            final ObjectDatabase database, final ObjectId defaultMetadataId,
            final @Nullable ReferencedEnvelope bboxFilter,
            final ProgressListener progressListener) {

        Iterator<NodeRef> nodes;
        {
            DepthTreeIterator iterator = new DepthTreeIterator("", defaultMetadataId, typeTree,
                    database, Strategy.FEATURES_ONLY);

            if (bboxFilter != null) {
                Predicate<Bounded> bboxPredicate = new BBoxPredicate(database, bboxFilter,
                        defaultMetadataId);
                iterator.setBoundsFilter(bboxPredicate);
            }
            nodes = iterator;
        }
        BulkFeatureRetriever gf = new BulkFeatureRetriever(database);
        Iterator<SimpleFeature> feats = gf.getGeoToolsFeatures(nodes);

        Iterator<SimpleFeature> transformedIter = Iterators.transform(feats,
                new Function<SimpleFeature, SimpleFeature>() {

                    private AtomicInteger count = new AtomicInteger();

                    @Override
                    public SimpleFeature apply(SimpleFeature input) {
                        progressListener
                                .setProgress((count.incrementAndGet() * 100.f) / typeTree.size());
                        return input;
                    }
                });
        return transformedIter;
    }

    private Iterator<SimpleFeature> adaptToArguments(final Iterator<SimpleFeature> plainFeatures,
            final ObjectId defaultMetadataId) {

        Iterator<SimpleFeature> features = plainFeatures;

        if (alter) {
            ObjectId featureTypeId = this.filterFeatureTypeId == null ? defaultMetadataId
                    : this.filterFeatureTypeId;
            features = alter(features, featureTypeId);

        } else if (forceExportDefaultFeatureType) {

            features = filter(features, defaultMetadataId);

        } else if (this.filterFeatureTypeId != null) {

            features = filter(features, filterFeatureTypeId);

        } else {

            features = force(features, defaultMetadataId);

        }

        return features;
    }

    private Iterator<SimpleFeature> force(Iterator<SimpleFeature> plainFeatures,
            final ObjectId forceMetadataId) {

        return Iterators.filter(plainFeatures, new Predicate<SimpleFeature>() {
            @Override
            public boolean apply(SimpleFeature input) {
                RevFeatureType type;
                type = (RevFeatureType) input.getUserData().get(RevFeatureType.class);
                ObjectId metadataId = type.getId();
                if (!forceMetadataId.equals(metadataId)) {
                    throw new GeoToolsOpException(StatusCode.MIXED_FEATURE_TYPES);
                }
                return true;
            }
        });
    }

    private Iterator<SimpleFeature> filter(Iterator<SimpleFeature> plainFeatures,
            final ObjectId filterFeatureTypeId) {

        return Iterators.filter(plainFeatures, new Predicate<SimpleFeature>() {
            @Override
            public boolean apply(SimpleFeature input) {
                RevFeatureType type;
                type = (RevFeatureType) input.getUserData().get(RevFeatureType.class);
                ObjectId metadataId = type.getId();
                boolean applies = filterFeatureTypeId.equals(metadataId);
                return applies;
            }
        });
    }

    private Iterator<SimpleFeature> alter(Iterator<SimpleFeature> plainFeatures,
            final ObjectId targetFeatureTypeId) {

        final RevFeatureType targetType = objectDatabase().getFeatureType(targetFeatureTypeId);

        final Function<SimpleFeature, SimpleFeature> alterFunction = (sf) -> {
            final RevFeatureType oldFeatureType;
            oldFeatureType = (RevFeatureType) sf.getUserData().get(RevFeatureType.class);

            final ObjectId metadataId = oldFeatureType.getId();
            if (targetType.getId().equals(metadataId)) {
                return sf;
            }

            final RevFeature oldFeature;
            oldFeature = (RevFeature) sf.getUserData().get(RevFeature.class);

            ImmutableList<PropertyDescriptor> oldAttributes = oldFeatureType.descriptors();
            ImmutableList<PropertyDescriptor> newAttributes = targetType.descriptors();

            RevFeatureBuilder builder = RevFeature.builder();
            for (int i = 0; i < newAttributes.size(); i++) {
                int idx = oldAttributes.indexOf(newAttributes.get(i));
                if (idx != -1) {
                    Optional<Object> oldValue = oldFeature.get(idx);
                    builder.addValue(oldValue.orNull());
                } else {
                    builder.addValue(null);
                }
            }
            RevFeature newFeature = builder.build();
            FeatureBuilder featureBuilder = new FeatureBuilder(targetType);
            SimpleFeature feature = (SimpleFeature) featureBuilder.build(sf.getID(), newFeature);
            return feature;
        };

        return Iterators.transform(plainFeatures, alterFunction);
    }

    private NodeRef resolTypeTreeRef(final String refspec, final String treePath,
            final RevTree rootTree) {
        Optional<NodeRef> typeTreeRef = command(FindTreeChild.class).setParent(rootTree)
                .setChildPath(treePath).call();
        checkArgument(typeTreeRef.isPresent(), "Type tree %s does not exist", refspec);
        checkArgument(TYPE.TREE.equals(typeTreeRef.get().getType()), "%s did not resolve to a tree",
                refspec);
        return typeTreeRef.get();
    }

    private RevTree resolveRootTree(final String refspec) {
        Optional<ObjectId> rootTreeId = command(ResolveTreeish.class)
                .setTreeish(refspec.substring(0, refspec.indexOf(':'))).call();

        checkArgument(rootTreeId.isPresent(), "Invalid tree spec: %s",
                refspec.substring(0, refspec.indexOf(':')));

        RevTree rootTree = objectDatabase().getTree(rootTreeId.get());
        return rootTree;
    }

    private String resolveRefSpec() {
        final String refspec;
        if (path.contains(":")) {
            refspec = path;
        } else {
            refspec = "WORK_HEAD:" + path;
        }
        return refspec;
    }

    private SimpleFeatureStore getTargetStore() {
        SimpleFeatureStore targetStore;
        try {
            targetStore = targetStoreProvider.get();
        } catch (Exception e) {
            throw new GeoToolsOpException(StatusCode.CANNOT_CREATE_FEATURESTORE);
        }
        if (targetStore == null) {
            throw new GeoToolsOpException(StatusCode.CANNOT_CREATE_FEATURESTORE);
        }
        return targetStore;
    }

    /**
     * 
     * @param featureStore a supplier that resolves to the feature store to use for exporting
     * @return
     */
    public ExportOp setFeatureStore(Supplier<SimpleFeatureStore> featureStore) {
        this.targetStoreProvider = featureStore;
        return this;
    }

    /**
     * 
     * @param featureStore the feature store to use for exporting
     * @return
     */
    public ExportOp setFeatureStore(SimpleFeatureStore featureStore) {
        this.targetStoreProvider = Suppliers.ofInstance(featureStore);
        return this;
    }

    /**
     * @param path the path to export Supports the [refspec]:[path] syntax
     * @return {@code this}
     */
    public ExportOp setPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * @param featureType the Id of the featureType of the features to export. If this is provided,
     *        only features with this feature type will be exported. If this is not provided and the
     *        path to export contains features with several different feature types, an exception
     *        will be throw to warn about it
     * @return {@code this}
     */
    public ExportOp setFilterFeatureTypeId(ObjectId featureTypeId) {
        this.filterFeatureTypeId = featureTypeId;
        return this;
    }

    /**
     * If set to true, all features will be exported, even if they do not have
     * 
     * @param alter whether to alter features before exporting, if they do not have the output
     *        feature type
     * @return
     */
    public ExportOp setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    /**
     * @param bboxFilter if provided, the bounding box filter to apply when exporting
     */
    public ExportOp setBBoxFilter(@Nullable ReferencedEnvelope bboxFilter) {
        this.bboxFilter = bboxFilter;
        return this;
    }

    /**
     * Calling this method causes the export operation to be performed in case the features in the
     * specified path have several different feature types, without throwing an exception. Only
     * features with the default feature type of the path will be exported. This has the same effect
     * as calling the setFeatureType method with the Id of the default feature type of the path to
     * export
     * 
     * If both this method and setFeatureId are used, this one will have priority
     * 
     * @return {@code this}
     */
    public ExportOp exportDefaultFeatureType() {
        this.forceExportDefaultFeatureType = true;
        return this;
    }

    /**
     * Sets the function to use for creating a valid Feature that has the FeatureType of the output
     * FeatureStore, based on the actual FeatureType of the Features to export.
     * 
     * The Export operation assumes that the feature returned by this function are valid to be added
     * to the current FeatureSource, and, therefore, performs no checking of FeatureType matching.
     * It is up to the user performing the export to ensure that the function actually generates
     * valid features for the current FeatureStore.
     * 
     * If no function is explicitly set, an identity function is used, and Features are not
     * converted.
     * 
     * This function can be used as a filter as well. If the returned object is Optional.absent, no
     * feature will be added
     * 
     * @param function
     * @return {@code this}
     */
    public ExportOp setFeatureTypeConversionFunction(
            Function<Feature, Optional<Feature>> function) {
        this.function = function == null ? IDENTITY : function;
        return this;
    }

    /**
     * @param transactional whether to use a geotools transaction for the operation, defaults to
     *        {@code true}
     */
    public ExportOp setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    private static class BBoxPredicate implements Predicate<Bounded> {

        private final ObjectStore store;

        private ReferencedEnvelope bbox;

        private ConcurrentMap<ObjectId, Envelope> filterByMetadataIdCRS = new ConcurrentHashMap<>();

        private final ObjectId defaultMetadataId;

        public BBoxPredicate(final ObjectStore store, final ReferencedEnvelope bbox,
                final ObjectId defaultMetadataId) {
            this.store = store;
            this.bbox = bbox;
            this.defaultMetadataId = defaultMetadataId;
        }

        @Override
        public boolean apply(Bounded input) {
            final ObjectId metadataId = getMetadataId(input);
            Envelope projectedFilter = getProjectedFilter(metadataId);
            boolean applies = input.intersects(projectedFilter);
            return applies;
        }

        private ObjectId getMetadataId(Bounded input) {
            if (input instanceof Bucket) {
                return defaultMetadataId;
            }
            if (input instanceof Node) {
                return ((Node) input).getMetadataId().or(defaultMetadataId);
            }
            return ((NodeRef) input).getMetadataId();
        }

        private Envelope getProjectedFilter(final ObjectId metadataId) {
            Envelope projectedFilter = filterByMetadataIdCRS.get(metadataId);
            if (projectedFilter == null) {
                projectedFilter = project(bbox, metadataId);
                filterByMetadataIdCRS.put(metadataId, projectedFilter);
            }
            return projectedFilter;
        }

        private Envelope project(ReferencedEnvelope bbox, ObjectId metadataId) {
            RevFeatureType featureType = store.getFeatureType(metadataId);
            CoordinateReferenceSystem targetCRS = featureType.type().getCoordinateReferenceSystem();
            Envelope transformed;
            try {
                CoordinateReferenceSystem sourceCRS = bbox.getCoordinateReferenceSystem();
                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);
                transformed = JTS.transform(bbox, null, transform, 10);
            } catch (FactoryException | TransformException e) {
                throw new RuntimeException(e);
            }
            return transformed;
        }
    }

}
