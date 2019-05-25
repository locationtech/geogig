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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.geotools.data.EmptyFeatureReader;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.ResourceInfo;
import org.geotools.data.Transaction;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureStore;
import org.geotools.data.store.ContentState;
import org.geotools.data.store.FeatureIteratorIterator;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.FeatureReaderIterator;
import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.factory.Hints;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 *
 */
@SuppressWarnings("unchecked")
public class GeogigFeatureStore extends ContentFeatureStore {

    /**
     * geogig feature source to delegate to, we do this b/c we can't inherit from both
     * ContentFeatureStore and {@link GeogigFeatureSource} at the same time
     */
    final GeogigFeatureSource delegate;

    // default geotools behaviour, we might want to avoid creating large lists of feature ids if
    // unnecessary
    private boolean returnFidsOnInsert = true;

    /**
     * @param entry
     */
    public GeogigFeatureStore(ContentEntry entry) {
        super(entry, (Query) null);
        delegate = new GeogigFeatureSource(entry) {
            @Override
            public void setTransaction(Transaction transaction) {
                super.setTransaction(transaction);

                // keep this feature store in sync
                GeogigFeatureStore.this.setTransaction(transaction);
            }
        };
        super.hints = (Set<Hints.Key>) (Set<?>) delegate.getSupportedHints();
    }

    public void setReturnFidsOnInsert(boolean createFidList) {
        this.returnFidsOnInsert = createFidList;
    }

    /** We handle events internally */
    protected boolean canEvent() {
        return true;
    }

    @Override
    public GeoGigDataStore getDataStore() {
        return delegate.getDataStore();
    }

    public GeogigFeatureSource getFeatureSource() {
        return delegate;
    }

    @Override
    public ContentEntry getEntry() {
        return delegate.getEntry();
    }

    @Override
    public ResourceInfo getInfo() {
        return delegate.getInfo();
    }

    @Override
    public org.opengis.feature.type.Name getName() {
        return delegate.getName();
    }

    @Override
    public QueryCapabilities getQueryCapabilities() {
        return delegate.getQueryCapabilities();
    }

    @Override
    public ContentState getState() {
        return delegate.getState();
    }

    @Override
    public synchronized Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public synchronized void setTransaction(Transaction transaction) {
        // we need to set both super and delegate transactions.
        super.setTransaction(transaction);

        // this guard ensures that a recursive loop will not form
        if (delegate.getTransaction() != transaction) {
            delegate.setTransaction(transaction);
        }
        if (!Transaction.AUTO_COMMIT.equals(transaction)) {
            GeogigTransactionState geogigTx;
            geogigTx = (GeogigTransactionState) transaction.getState(GeogigTransactionState.class);
            if (geogigTx == null) {
                geogigTx = new GeogigTransactionState(getEntry());
                transaction.putState(GeogigTransactionState.class, geogigTx);
            }
        }
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {
        return delegate.buildFeatureType();
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        return delegate.getCount(query);
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        return delegate.getBoundsInternal(query);
    }

    @Override
    protected boolean canFilter() {
        return delegate.canFilter();
    }

    @Override
    protected boolean canSort() {
        return delegate.canSort();
    }

    @Override
    protected boolean canRetype() {
        return delegate.canRetype();
    }

    @Override
    protected boolean canLimit() {
        return delegate.canLimit();
    }

    @Override
    protected boolean canOffset() {
        return delegate.canOffset();
    }

    @Override
    protected boolean canTransact() {
        return delegate.canTransact();
    }

    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {
        return delegate.getReaderInternal(query);
    }

    public @Override @VisibleForTesting boolean handleVisitor(Query query, FeatureVisitor visitor) {
        return delegate.handleVisitor(query, visitor);
    }

    @Override
    protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(Query query,
            final int flags) throws IOException {

        Preconditions.checkArgument(flags != 0, "no write flags set");
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");

        FeatureReader<SimpleFeatureType, SimpleFeature> features;
        if ((flags | WRITER_UPDATE) == WRITER_UPDATE) {
            features = delegate.getReader(query);
        } else {
            features = new EmptyFeatureReader<SimpleFeatureType, SimpleFeature>(getSchema());
        }

        final NodeRef typeRef = delegate.getTypeRef();
        WorkingTree wtree = getFeatureSource().getWorkingTree();

        GeoGigFeatureWriter writer;
        if ((flags | WRITER_ADD) == WRITER_ADD) {
            writer = GeoGigFeatureWriter.createAppendable(features, typeRef, wtree);
        } else {
            writer = GeoGigFeatureWriter.create(features, typeRef, wtree);
        }
        return writer;
    }

    @Override
    public final List<FeatureId> addFeatures(
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection)
            throws IOException {

        // Preconditions.checkState(getDataStore().isAllowTransactions(),
        // "Transactions not supported; head is not a local branch");
        final WorkingTree workingTree = delegate.getWorkingTree();

        ProgressListener listener = new DefaultProgressListener();

        final FeatureType nativeSchema = delegate.getNativeType().type();
        final NodeRef typeRef = delegate.getTypeRef();
        final String treePath = typeRef.path();
        final ObjectId featureTypeId = typeRef.getMetadataId();

        List<FeatureId> insertedFids = returnFidsOnInsert ? new ArrayList<>()
                : Collections.emptyList();

        try (FeatureIterator<SimpleFeature> featureIterator = featureCollection.features()) {
            Iterator<SimpleFeature> gtFeatures = new FeatureIteratorIterator<SimpleFeature>(
                    featureIterator);
            /*
             * Make sure to transform the incoming features to the native schema to avoid situations
             * where geogig would change the metadataId of the RevFeature nodes due to small
             * differences in the default and incoming schema such as namespace or missing
             * properties
             */
            SchemaInforcer schemaInforcer = new SchemaInforcer(nativeSchema);
            Iterator<org.locationtech.geogig.feature.Feature> features = Iterators
                    .transform(gtFeatures, schemaInforcer::apply);
            // the returned list is expected to be in the order provided by the argument feature
            // collection, so lets add the fids in the transformer here

            Function<org.locationtech.geogig.feature.Feature, FeatureInfo> fn = new Function<org.locationtech.geogig.feature.Feature, FeatureInfo>() {
                @Override
                public FeatureInfo apply(org.locationtech.geogig.feature.Feature f) {
                    RevFeature feature = RevFeature.builder().build(f);
                    String fid = f.getId();
                    String path = NodeRef.appendChild(treePath, fid);
                    String version = feature.getId().toString();
                    if (returnFidsOnInsert) {
                        insertedFids.add(new FeatureIdVersionedImpl(fid, version));
                    }
                    return FeatureInfo.insert(feature, featureTypeId, path);
                }
            };

            Iterator<FeatureInfo> featureInfos = Iterators.transform(features, fn::apply);

            workingTree.insert(featureInfos, listener);
        } catch (Exception e) {
            Throwables.throwIfInstanceOf(e, IOException.class);
            throw new IOException(e);
        }
        return insertedFids;
    }

    /**
     * Function used when inserted to check whether the {@link Hints#USE_PROVIDED_FID} in a Feature
     * {@link Feature#getUserData() user data} map is set to {@code Boolean.TRUE}, and only if so
     * let the feature unchanged, otherwise return a feature with the exact same contents but a
     * newly generaged feature id.
     * <p>
     * This class also creates a feature with the full native schema in case the input feature is a
     * reduced version.
     */
    private static class SchemaInforcer
            implements Function<SimpleFeature, org.locationtech.geogig.feature.Feature> {

        private static final AtomicLong seq = new AtomicLong();

        private final String baseId;

        private final String nativeTypeName;

        private final FeatureType targetSchema;

        public SchemaInforcer(final FeatureType targetSchema) {
            this.targetSchema = targetSchema;
            this.nativeTypeName = targetSchema.getName().getLocalPart();
            Hasher hasher = Hashing.murmur3_32().newHasher();
            hasher.putString(targetSchema.getName().getLocalPart(), Charsets.UTF_8);
            hasher.putLong(System.currentTimeMillis());
            hasher.putLong(System.nanoTime());
            baseId = hasher.hash().toString();
        }

        @Override
        public org.locationtech.geogig.feature.Feature apply(SimpleFeature input) {
            final SimpleFeatureType featureType = input.getType();
            final String typeName = featureType.getTypeName();
            Preconditions.checkArgument(nativeTypeName.equals(typeName),
                    "Tried to insert features of type '%s' into '%s'", featureType.getTypeName(),
                    nativeTypeName);

            String id;
            if (Boolean.TRUE.equals(input.getUserData().get(Hints.USE_PROVIDED_FID))) {
                Object providedFid = input.getUserData().get(Hints.PROVIDED_FID);
                if (providedFid == null) {
                    id = input.getID();
                } else {
                    id = String.valueOf(providedFid);
                }
            } else {
                id = baseId + seq.incrementAndGet();
            }

            org.locationtech.geogig.feature.Feature feature;
            feature = org.locationtech.geogig.feature.Feature.build(id, this.targetSchema);

            for (int i = 0; i < featureType.getAttributeCount(); i++) {
                String name = featureType.getDescriptor(i).getLocalName();
                feature.setAttribute(name, input.getAttribute(name));
            }
            return feature;
        }
    };

    @Override
    public void modifyFeatures(org.opengis.feature.type.Name[] names, Object[] values,
            Filter filter) throws IOException {
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");

        final WorkingTree workingTree = delegate.getWorkingTree();
        final FeatureType nativeSchema = delegate.getNativeType().type();
        final NodeRef typeRef = delegate.getTypeRef();
        final String treePath = typeRef.path();
        final ObjectId featureTypeId = typeRef.getMetadataId();

        try (AutoCloseableIterator<SimpleFeature> gtFeatures = modifyingFeatureIterator(names,
                values, filter)) {
            /*
             * Make sure to transform the incoming features to the native schema to avoid situations
             * where geogig would change the metadataId of the RevFeature nodes due to small
             * differences in the default and incoming schema such as namespace or missing
             * properties
             */
            SchemaInforcer schemaInforcer = new SchemaInforcer(nativeSchema);
            Iterator<org.locationtech.geogig.feature.Feature> schemaEnforced = Iterators
                    .transform(gtFeatures, schemaInforcer::apply);

            ProgressListener listener = new DefaultProgressListener();

            Function<org.locationtech.geogig.feature.Feature, FeatureInfo> fn = new Function<org.locationtech.geogig.feature.Feature, FeatureInfo>() {
                @Override
                public FeatureInfo apply(org.locationtech.geogig.feature.Feature f) {
                    return FeatureInfo.insert(RevFeature.builder().build(f), featureTypeId,
                            NodeRef.appendChild(treePath, f.getId()));
                }
            };

            Iterator<FeatureInfo> featureInfos = Iterators.transform(schemaEnforced, fn::apply);

            workingTree.insert(featureInfos, listener);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * @param names
     * @param values
     * @param filter
     * @return
     * @throws IOException
     */
    private AutoCloseableIterator<SimpleFeature> modifyingFeatureIterator(
            final org.opengis.feature.type.Name[] names, final Object[] values, final Filter filter)
            throws IOException {

        AutoCloseableIterator<SimpleFeature> iterator = featureIterator(filter);

        Function<SimpleFeature, SimpleFeature> modifyingFunction = new ModifyingFunction(names,
                values);

        AutoCloseableIterator<SimpleFeature> modifyingIterator = AutoCloseableIterator
                .transform(iterator, modifyingFunction);
        return modifyingIterator;
    }

    private AutoCloseableIterator<SimpleFeature> featureIterator(final Filter filter)
            throws IOException {
        FeatureReader<SimpleFeatureType, SimpleFeature> unchanged = getReader(filter);
        FeatureReaderIterator<SimpleFeature> iterator = new FeatureReaderIterator<SimpleFeature>(
                unchanged);
        AutoCloseableIterator<SimpleFeature> autocloseable = AutoCloseableIterator
                .fromIterator(iterator, (i) -> iterator.close());
        return autocloseable;
    }

    @Override
    public void removeFeatures(Filter filter) throws IOException {
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");
        final WorkingTree workingTree = delegate.getWorkingTree();
        final String typeTreePath = delegate.getTypeTreePath();
        filter = (Filter) filter.accept(new SimplifyingFilterVisitor(), null);
        if (Filter.INCLUDE.equals(filter)) {
            workingTree.truncate(typeTreePath);
            return;
        }
        if (Filter.EXCLUDE.equals(filter)) {
            return;
        }

        Iterator<SimpleFeature> featureIterator = featureIterator(filter);
        Iterator<String> affectedFeaturePaths = Iterators.transform(featureIterator,
                f -> NodeRef.appendChild(typeTreePath, f.getID()));
        workingTree.delete(affectedFeaturePaths, DefaultProgressListener.NULL);
    }

    /**
    *
    */
    private static final class ModifyingFunction implements Function<SimpleFeature, SimpleFeature> {

        private org.opengis.feature.type.Name[] names;

        private Object[] values;

        /**
         * @param names
         * @param values
         */
        public ModifyingFunction(org.opengis.feature.type.Name[] names, Object[] values) {
            this.names = names;
            this.values = values;
        }

        @Override
        public SimpleFeature apply(SimpleFeature input) {
            for (int i = 0; i < names.length; i++) {
                org.opengis.feature.type.Name attName = names[i];
                Object attValue = values[i];
                input.setAttribute(attName, attValue);
            }
            input.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            return input;
        }

    }
}
