/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.plumbing;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.Iterator;

import javax.annotation.Nullable;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.collection.BaseFeatureCollection;
import org.geotools.feature.collection.DelegateFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Internal operation for creating a FeatureCollection from a tree content.
 * 
 */

public class ExportDiffOp extends AbstractGeoGigOp<SimpleFeatureStore> {

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

    private boolean transactional;

    private boolean old;

    private String newRef;

    private String oldRef;

    /**
     * Executes the export operation using the parameters that have been specified.
     * 
     * @return a FeatureCollection with the specified features
     */
    @Override
    protected  SimpleFeatureStore _call() {

        final SimpleFeatureStore targetStore = getTargetStore();

        final String refspec = old ? oldRef : newRef;
        final RevTree rootTree = resolveRootTree(refspec);
        final NodeRef typeTreeRef = resolTypeTreeRef(refspec, path, rootTree);
        final ObjectId defaultMetadataId = typeTreeRef.getMetadataId();

        final ProgressListener progressListener = getProgressListener();

        progressListener.started();
        progressListener.setDescription("Exporting diffs for path '" + path + "'... ");

        FeatureCollection<SimpleFeatureType, SimpleFeature> asFeatureCollection = new BaseFeatureCollection<SimpleFeatureType, SimpleFeature>() {

            @Override
            public FeatureIterator<SimpleFeature> features() {

                Iterator<DiffEntry> diffs = command(DiffOp.class).setOldVersion(oldRef)
                        .setNewVersion(newRef).setFilter(path).call();

                final Iterator<SimpleFeature> plainFeatures = getFeatures(diffs, old, stagingDatabase(),
                        defaultMetadataId, progressListener);

                Iterator<Optional<Feature>> transformed = Iterators.transform(plainFeatures,
                        ExportDiffOp.this.function);

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

    private static Iterator<SimpleFeature> getFeatures(Iterator<DiffEntry> diffs,
            final boolean old, final ObjectDatabase database, final ObjectId metadataId,
            final ProgressListener progressListener) {

        final SimpleFeatureType featureType = addFidAttribute(database.getFeatureType(metadataId));
        final RevFeatureType revFeatureType = RevFeatureTypeImpl.build(featureType);
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);

        Function<DiffEntry, SimpleFeature> asFeature = new Function<DiffEntry, SimpleFeature>() {

            @Override
            @Nullable
            public SimpleFeature apply(final DiffEntry input) {
                NodeRef nodeRef = old ? input.getOldObject() : input.getNewObject();
                if (nodeRef == null) {
                    return null;
                }
                final RevFeature revFeature = database.getFeature(nodeRef.objectId());
                ImmutableList<Optional<Object>> values = revFeature.getValues();
                for (int i = 0; i < values.size(); i++) {
                    String name = featureType.getDescriptor(i + 1).getLocalName();
                    Object value = values.get(i).orNull();
                    featureBuilder.set(name, value);
                }
                featureBuilder.set("geogig_fid", nodeRef.name());
                Feature feature = featureBuilder.buildFeature(nodeRef.name());
                feature.getUserData().put(Hints.USE_PROVIDED_FID, true);
                feature.getUserData().put(RevFeature.class, revFeature);
                feature.getUserData().put(RevFeatureType.class, revFeatureType);

                if (feature instanceof SimpleFeature) {
                    return (SimpleFeature) feature;
                }
                return null;
            }

        };

        Iterator<SimpleFeature> asFeatures = Iterators.transform(diffs, asFeature);

        UnmodifiableIterator<SimpleFeature> filterNulls = Iterators.filter(asFeatures,
                Predicates.notNull());

        return filterNulls;
    }

    private static SimpleFeatureType addFidAttribute(RevFeatureType revFType) {
        SimpleFeatureType featureType = (SimpleFeatureType) revFType.type();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add("geogig_fid", String.class);
        for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
            builder.add(descriptor);
        }
        builder.setName(featureType.getName());
        builder.setCRS(featureType.getCoordinateReferenceSystem());
        featureType = builder.buildFeatureType();
        return featureType;
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
        Optional<ObjectId> rootTreeId = command(ResolveTreeish.class).setTreeish(refspec).call();

        checkArgument(rootTreeId.isPresent(), "Invalid tree spec: %s", refspec);

        RevTree rootTree = stagingDatabase().getTree(rootTreeId.get());
        return rootTree;
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
    public ExportDiffOp setFeatureStore(Supplier<SimpleFeatureStore> featureStore) {
        this.targetStoreProvider = featureStore;
        return this;
    }

    /**
     * 
     * @param featureStore the feature store to use for exporting The schema of the feature store
     *        must be equal to the one of the layer whose diffs are to be exported, plus an
     *        additional "geogig_fid" field of type String, which is used to include the id of each
     *        feature.
     * 
     * @return
     */
    public ExportDiffOp setFeatureStore(SimpleFeatureStore featureStore) {
        this.targetStoreProvider = Suppliers.ofInstance(featureStore);
        return this;
    }

    /**
     * @param path the path to export
     * @return {@code this}
     */
    public ExportDiffOp setPath(String path) {
        this.path = path;
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
    public ExportDiffOp setFeatureTypeConversionFunction(
            Function<Feature, Optional<Feature>> function) {
        this.function = function == null ? IDENTITY : function;
        return this;
    }

    /**
     * @param transactional whether to use a geotools transaction for the operation, defaults to
     *        {@code true}
     */
    public ExportDiffOp setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public ExportDiffOp setNewRef(String newRef) {
        this.newRef = newRef;
        return this;
    }

    public ExportDiffOp setOldRef(String oldRef) {
        this.oldRef = oldRef;
        return this;
    }

    public ExportDiffOp setUseOld(boolean old) {
        this.old = old;
        return this;
    }
}
