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

import java.io.IOException;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

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
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.FeatureReaderIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 *
 */
class GeogigFeatureStore extends ContentFeatureStore {

    /**
     * geogig feature source to delegate to, we do this b/c we can't inherit from both
     * ContentFeatureStore and {@link GeogigFeatureSource} at the same time
     */
    private GeogigFeatureSource delegate;

    /**
     * @param entry
     * @param query
     */
    public GeogigFeatureStore(ContentEntry entry) {
        super(entry, (Query) null);
        delegate = new GeogigFeatureSource(entry, query) {
            @Override
            public void setTransaction(Transaction transaction) {
                super.setTransaction(transaction);

                // keep this feature store in sync
                GeogigFeatureStore.this.setTransaction(transaction);
            }
        };
        super.hints = delegate.getSupportedHints();
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
    public Name getName() {
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

    @Override
    protected boolean handleVisitor(Query query, FeatureVisitor visitor) throws IOException {
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

        String path = delegate.getTypeTreePath();
        WorkingTree wtree = getFeatureSource().getWorkingTree();

        GeoGigFeatureWriter writer;
        if ((flags | WRITER_ADD) == WRITER_ADD) {
            writer = GeoGigFeatureWriter.createAppendable(features, path, wtree);
        } else {
            writer = GeoGigFeatureWriter.create(features, path, wtree);
        }
        return writer;
    }

    @Override
    public final List<FeatureId> addFeatures(
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection)
            throws IOException {

        if (Transaction.AUTO_COMMIT.equals(getTransaction())) {
            throw new UnsupportedOperationException("GeoGIG does not support AUTO_COMMIT");
        }
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");
        final WorkingTree workingTree = delegate.getWorkingTree();
        final String path = delegate.getTypeTreePath();

        ProgressListener listener = new DefaultProgressListener();

        final List<FeatureId> insertedFids = Lists.newArrayList();
        List<Node> deferringTarget = new AbstractList<Node>() {

            @Override
            public boolean add(Node node) {
                String fid = node.getName();
                String version = node.getObjectId().toString();
                insertedFids.add(new FeatureIdVersionedImpl(fid, version));
                return true;
            }

            @Override
            public Node get(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int size() {
                return 0;
            }
        };
        Integer count = (Integer) null;

        FeatureIterator<SimpleFeature> featureIterator = featureCollection.features();
        try {
            Iterator<SimpleFeature> features;
            features = new FeatureIteratorIterator<SimpleFeature>(featureIterator);
            /*
             * Make sure to transform the incoming features to the native schema to avoid situations
             * where geogig would change the metadataId of the RevFeature nodes due to small
             * differences in the default and incoming schema such as namespace or missing
             * properties
             */
            final SimpleFeatureType nativeSchema = delegate.getNativeType();

            features = Iterators.transform(features, new SchemaInforcer(nativeSchema));

            workingTree.insert(path, features, listener, deferringTarget, count);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            featureIterator.close();
        }

        return insertedFids;
    }

    /**
     * Function used when inserted to check whether the {@link Hints#USE_PROVIDED_FID} in a Feature
     * {@link Feature#getUserData() user data} map is set to {@code Boolean.TRUE}, and only if so
     * let the feature unchanged, otherwise return a feature with the exact same contents but a
     * newly generaged feature id.
     */
    private static class SchemaInforcer implements Function<SimpleFeature, SimpleFeature> {

        private SimpleFeatureBuilder builder;

        public SchemaInforcer(final SimpleFeatureType targetSchema) {
            this.builder = new SimpleFeatureBuilder(targetSchema);
        }

        @Override
        public SimpleFeature apply(SimpleFeature input) {
            builder.reset();

            for (int i = 0; i < input.getType().getAttributeCount(); i++) {
                String name = input.getType().getDescriptor(i).getLocalName();
                builder.set(name, input.getAttribute(name));
            }

            String id;
            if (Boolean.TRUE.equals(input.getUserData().get(Hints.USE_PROVIDED_FID))) {
                id = input.getID();
            } else {
                id = null;
            }

            SimpleFeature feature = builder.buildFeature(id);
            if (!input.getUserData().isEmpty()) {
                feature.getUserData().putAll(input.getUserData());
            }
            return feature;
        }
    };

    @Override
    public void modifyFeatures(Name[] names, Object[] values, Filter filter) throws IOException {
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");

        final WorkingTree workingTree = delegate.getWorkingTree();
        final String path = delegate.getTypeTreePath();
        Iterator<SimpleFeature> features = modifyingFeatureIterator(names, values, filter);
        /*
         * Make sure to transform the incoming features to the native schema to avoid situations
         * where geogig would change the metadataId of the RevFeature nodes due to small differences
         * in the default and incoming schema such as namespace or missing properties
         */
        final SimpleFeatureType nativeSchema = delegate.getNativeType();

        features = Iterators.transform(features, new SchemaInforcer(nativeSchema));

        try {
            ProgressListener listener = new DefaultProgressListener();
            Integer count = (Integer) null;
            List<Node> target = (List<Node>) null;
            workingTree.insert(path, features, listener, target, count);
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
    private Iterator<SimpleFeature> modifyingFeatureIterator(final Name[] names,
            final Object[] values, final Filter filter) throws IOException {

        Iterator<SimpleFeature> iterator = featureIterator(filter);

        Function<SimpleFeature, SimpleFeature> modifyingFunction = new ModifyingFunction(names,
                values);

        Iterator<SimpleFeature> modifyingIterator = Iterators
                .transform(iterator, modifyingFunction);
        return modifyingIterator;
    }

    private Iterator<SimpleFeature> featureIterator(final Filter filter) throws IOException {
        FeatureReader<SimpleFeatureType, SimpleFeature> unchanged = getReader(filter);
        Iterator<SimpleFeature> iterator = new FeatureReaderIterator<SimpleFeature>(unchanged);
        return iterator;
    }

    @Override
    public void removeFeatures(Filter filter) throws IOException {
        Preconditions.checkState(getDataStore().isAllowTransactions(),
                "Transactions not supported; head is not a local branch");
        final WorkingTree workingTree = delegate.getWorkingTree();
        final String typeTreePath = delegate.getTypeTreePath();
        filter = (Filter) filter.accept(new SimplifyingFilterVisitor(), null);
        if (Filter.INCLUDE.equals(filter)) {
            workingTree.delete(typeTreePath);
            return;
        }
        if (Filter.EXCLUDE.equals(filter)) {
            return;
        }

        Iterator<SimpleFeature> featureIterator = featureIterator(filter);
        Iterator<String> affectedFeaturePaths = Iterators.transform(featureIterator,
                new Function<SimpleFeature, String>() {

                    @Override
                    public String apply(SimpleFeature input) {
                        String fid = input.getID();
                        return NodeRef.appendChild(typeTreePath, fid);
                    }
                });
        workingTree.delete(affectedFeaturePaths);
    }

    /**
    *
    */
    private static final class ModifyingFunction implements Function<SimpleFeature, SimpleFeature> {

        private Name[] names;

        private Object[] values;

        /**
         * @param names
         * @param values
         */
        public ModifyingFunction(Name[] names, Object[] values) {
            this.names = names;
            this.values = values;
        }

        @Override
        public SimpleFeature apply(SimpleFeature input) {
            for (int i = 0; i < names.length; i++) {
                Name attName = names[i];
                Object attValue = values[i];
                input.setAttribute(attName, attValue);
            }
            input.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            return input;
        }

    }
}
