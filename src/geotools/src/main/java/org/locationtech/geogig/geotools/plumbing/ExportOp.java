/* Copyright (c) 2012-2014 Boundless and others.
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.collection.BaseFeatureCollection;
import org.geotools.feature.collection.DelegateFeatureIterator;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureImpl;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.hooks.Hookable;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

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
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Internal operation for creating a FeatureCollection from a tree content.
 * 
 */
@Hookable(name = "export")
public class ExportOp extends AbstractGeoGigOp<SimpleFeatureStore> {

    private static final Function<Feature, Optional<Feature>> IDENTITY = new Function<Feature, Optional<Feature>>() {

        @Override
        @Nullable
        public Optional<Feature> apply(@Nullable Feature feature) {
            return Optional.fromNullable(feature);
        }

    };

    private String path;

    private Supplier<SimpleFeatureStore> targetStoreProvider;

    private Function<Feature, Optional<Feature>> function = IDENTITY;

    private ObjectId filterFeatureTypeId;

    private boolean forceExportDefaultFeatureType;

    private boolean alter;

    private boolean transactional;

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
        final StagingDatabase database = stagingDatabase();
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

        final RevTree typeTree = database.getTree(typeTreeRef.objectId());

        final ProgressListener progressListener = getProgressListener();

        progressListener.started();
        progressListener.setDescription("Exporting from " + path + " to "
                + targetStore.getName().getLocalPart() + "... ");

        FeatureCollection<SimpleFeatureType, SimpleFeature> asFeatureCollection = new BaseFeatureCollection<SimpleFeatureType, SimpleFeature>() {

            @Override
            public FeatureIterator<SimpleFeature> features() {

                final Iterator<SimpleFeature> plainFeatures = getFeatures(typeTree, database,
                        defaultMetadataId, progressListener);

                Iterator<SimpleFeature> adaptedFeatures = adaptToArguments(plainFeatures,
                        defaultMetadataId);

                Iterator<Optional<Feature>> transformed = Iterators.transform(adaptedFeatures,
                        ExportOp.this.function);

                Iterator<SimpleFeature> filtered = Iterators.filter(Iterators.transform(
                        transformed, new Function<Optional<Feature>, SimpleFeature>() {
                            @Override
                            public SimpleFeature apply(Optional<Feature> input) {
                                return (SimpleFeature) (input.isPresent() ? input.get() : null);
                            }
                        }), Predicates.notNull());

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
                transaction.commit();
            } catch (final Exception e) {
                if (transactional) {
                    transaction.rollback();
                }
                Throwables.propagateIfInstanceOf(e, GeoToolsOpException.class);
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
            final ProgressListener progressListener) {

        Iterator<NodeRef> nodes = new DepthTreeIterator("", defaultMetadataId, typeTree, database,
                Strategy.FEATURES_ONLY);

        // progress reporting
        nodes = Iterators.transform(nodes, new Function<NodeRef, NodeRef>() {

            private AtomicInteger count = new AtomicInteger();

            @Override
            public NodeRef apply(NodeRef input) {
                progressListener.setProgress((count.incrementAndGet() * 100.f) / typeTree.size());
                return input;
            }
        });

        Function<NodeRef, SimpleFeature> asFeature = new Function<NodeRef, SimpleFeature>() {

            private Map<ObjectId, FeatureBuilder> ftCache = Maps.newHashMap();

            @Override
            @Nullable
            public SimpleFeature apply(final NodeRef input) {
                final ObjectId metadataId = input.getMetadataId();
                final RevFeature revFeature = database.getFeature(input.objectId());

                FeatureBuilder featureBuilder = getBuilderFor(metadataId);
                Feature feature = featureBuilder.build(input.name(), revFeature);
                feature.getUserData().put(Hints.USE_PROVIDED_FID, true);
                feature.getUserData().put(RevFeature.class, revFeature);
                feature.getUserData().put(RevFeatureType.class, featureBuilder.getType());

                if (feature instanceof SimpleFeature) {
                    return (SimpleFeature) feature;
                }
                return null;
            }

            private FeatureBuilder getBuilderFor(final ObjectId metadataId) {
                FeatureBuilder featureBuilder = ftCache.get(metadataId);
                if (featureBuilder == null) {
                    RevFeatureType revFtype = database.getFeatureType(metadataId);
                    featureBuilder = new FeatureBuilder(revFtype);
                    ftCache.put(metadataId, featureBuilder);
                }
                return featureBuilder;
            }
        };

        Iterator<SimpleFeature> asFeatures = Iterators.transform(nodes, asFeature);

        UnmodifiableIterator<SimpleFeature> filterNulls = Iterators.filter(asFeatures,
                Predicates.notNull());

        return filterNulls;
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

        final RevFeatureType targetType = stagingDatabase().getFeatureType(targetFeatureTypeId);

        Function<SimpleFeature, SimpleFeature> alterFunction = new Function<SimpleFeature, SimpleFeature>() {
            @Override
            public SimpleFeature apply(SimpleFeature input) {
                final RevFeatureType oldFeatureType;
                oldFeatureType = (RevFeatureType) input.getUserData().get(RevFeatureType.class);

                final ObjectId metadataId = oldFeatureType.getId();
                if (targetType.getId().equals(metadataId)) {
                    return input;
                }

                final RevFeature oldFeature;
                oldFeature = (RevFeature) input.getUserData().get(RevFeature.class);

                ImmutableList<PropertyDescriptor> oldAttributes = oldFeatureType
                        .sortedDescriptors();
                ImmutableList<PropertyDescriptor> newAttributes = targetType.sortedDescriptors();

                ImmutableList<Optional<Object>> oldValues = oldFeature.getValues();
                List<Optional<Object>> newValues = Lists.newArrayList();
                for (int i = 0; i < newAttributes.size(); i++) {
                    int idx = oldAttributes.indexOf(newAttributes.get(i));
                    if (idx != -1) {
                        Optional<Object> oldValue = oldValues.get(idx);
                        newValues.add(oldValue);
                    } else {
                        newValues.add(Optional.absent());
                    }
                }
                RevFeature newFeature = RevFeatureImpl.build(ImmutableList.copyOf(newValues));
                FeatureBuilder featureBuilder = new FeatureBuilder(targetType);
                SimpleFeature feature = (SimpleFeature) featureBuilder.build(input.getID(),
                        newFeature);
                return feature;
            }
        };
        return Iterators.transform(plainFeatures, alterFunction);
    }

    private NodeRef resolTypeTreeRef(final String refspec, final String treePath,
            final RevTree rootTree) {
        Optional<NodeRef> typeTreeRef = command(FindTreeChild.class).setIndex(true)
                .setParent(rootTree).setChildPath(treePath).call();
        checkArgument(typeTreeRef.isPresent(), "Type tree %s does not exist", refspec);
        checkArgument(TYPE.TREE.equals(typeTreeRef.get().getType()),
                "%s did not resolve to a tree", refspec);
        return typeTreeRef.get();
    }

    private RevTree resolveRootTree(final String refspec) {
        Optional<ObjectId> rootTreeId = command(ResolveTreeish.class).setTreeish(
                refspec.substring(0, refspec.indexOf(':'))).call();

        checkArgument(rootTreeId.isPresent(), "Invalid tree spec: %s",
                refspec.substring(0, refspec.indexOf(':')));

        RevTree rootTree = stagingDatabase().getTree(rootTreeId.get());
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
     * @param alter whther to alter features before exporting, if they do not have the output
     *        feature type
     * @return
     */
    public ExportOp setAlter(boolean alter) {
        this.alter = alter;
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
    public ExportOp setFeatureTypeConversionFunction(Function<Feature, Optional<Feature>> function) {
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
}
