/* Copyright (c) 2014-2016 Boundless and others.
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
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectStore;
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
import com.google.common.collect.Iterators;

/**
 * Internal operation for creating a FeatureCollection from a tree content.
 * 
 */

public class ExportDiffOp extends AbstractGeoGigOp<SimpleFeatureStore> {

    public static final String CHANGE_TYPE_NAME = "changetype";

    private static final Function<Feature, Optional<Feature>> IDENTITY = (feature) -> Optional
            .fromNullable(feature);

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
    protected SimpleFeatureStore _call() {

        final SimpleFeatureStore targetStore = getTargetStore();

        final String refspec = old ? oldRef : newRef;
        final RevTree rootTree = resolveRootTree(refspec);
        final NodeRef typeTreeRef = resolTypeTreeRef(refspec, path, rootTree);
        final ObjectId defaultMetadataId = typeTreeRef.getMetadataId();

        final ProgressListener progressListener = getProgressListener();

        progressListener.started();
        progressListener.setDescription("Exporting diffs for path '" + path + "'... ");

        try (AutoCloseableIterator<DiffEntry> diffs = command(DiffOp.class).setOldVersion(oldRef)
                .setNewVersion(newRef).setFilter(path).call()) {
            FeatureCollection<SimpleFeatureType, SimpleFeature> asFeatureCollection = new BaseFeatureCollection<SimpleFeatureType, SimpleFeature>() {

                @Override
                public FeatureIterator<SimpleFeature> features() {

                    final Iterator<SimpleFeature> plainFeatures = getFeatures(diffs, old,
                            objectDatabase(), defaultMetadataId, progressListener);

                    Iterator<Optional<Feature>> transformed = Iterators.transform(plainFeatures,
                            ExportDiffOp.this.function);

                    // (f) -> (SimpleFeature) (f.isPresent() ? f.get() : null)
                    Function<Optional<Feature>, SimpleFeature> fn = new Function<Optional<Feature>, SimpleFeature>() {
                        @Override
                        public SimpleFeature apply(Optional<Feature> f) {
                            return (SimpleFeature) (f.isPresent() ? f.get() : null);
                        }
                    };

                    Iterator<SimpleFeature> filtered = Iterators
                            .filter(Iterators.transform(transformed, fn), Predicates.notNull());

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
                    Throwables.throwIfInstanceOf(e, GeoToolsOpException.class);
                    throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_ADD);
                } finally {
                    transaction.close();
                }
            } catch (IOException e) {
                throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_ADD);
            }
        }

        progressListener.complete();

        return targetStore;

    }

    private static AutoCloseableIterator<SimpleFeature> getFeatures(
            AutoCloseableIterator<DiffEntry> diffs, final boolean old, final ObjectStore database,
            final ObjectId metadataId, final ProgressListener progressListener) {

        final SimpleFeatureType featureType = addChangeTypeAttribute(
                database.getFeatureType(metadataId));
        final RevFeatureType revFeatureType = RevFeatureType.builder().type(featureType).build();
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);

        final Function<DiffEntry, SimpleFeature> asFeature = (de) -> {
            NodeRef nodeRef = old ? de.getOldObject() : de.getNewObject();
            if (nodeRef == null) {
                return null;
            }
            final RevFeature revFeature = database.getFeature(nodeRef.getObjectId());
            for (int i = 0; i < revFeature.size(); i++) {
                String name = featureType.getDescriptor(i + 1).getLocalName();
                Object value = revFeature.get(i).orNull();
                featureBuilder.set(name, value);
            }
            featureBuilder.set(CHANGE_TYPE_NAME, de.changeType().name().charAt(0));
            Feature feature = featureBuilder.buildFeature(nodeRef.name());
            feature.getUserData().put(Hints.USE_PROVIDED_FID, true);
            feature.getUserData().put(RevFeature.class, revFeature);
            feature.getUserData().put(RevFeatureType.class, revFeatureType);

            if (feature instanceof SimpleFeature) {
                return (SimpleFeature) feature;
            }
            return null;
        };

        AutoCloseableIterator<SimpleFeature> asFeatures = AutoCloseableIterator.transform(diffs,
                asFeature);

        AutoCloseableIterator<SimpleFeature> filterNulls = AutoCloseableIterator.filter(asFeatures,
                Predicates.notNull());

        return filterNulls;
    }

    private static SimpleFeatureType addChangeTypeAttribute(RevFeatureType revFType) {
        SimpleFeatureType featureType = (SimpleFeatureType) revFType.type();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add(CHANGE_TYPE_NAME, String.class);
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
        Optional<NodeRef> typeTreeRef = command(FindTreeChild.class).setParent(rootTree)
                .setChildPath(treePath).call();
        checkArgument(typeTreeRef.isPresent(), "Type tree %s does not exist", refspec);
        checkArgument(TYPE.TREE.equals(typeTreeRef.get().getType()), "%s did not resolve to a tree",
                refspec);
        return typeTreeRef.get();
    }

    private RevTree resolveRootTree(final String refspec) {
        Optional<ObjectId> rootTreeId = command(ResolveTreeish.class).setTreeish(refspec).call();

        checkArgument(rootTreeId.isPresent(), "Invalid tree spec: %s", refspec);

        RevTree rootTree = objectDatabase().getTree(rootTreeId.get());
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
