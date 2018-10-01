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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.Query;
import org.geotools.feature.visitor.FeatureAttributeVisitor;
import org.geotools.feature.visitor.FeatureCalc;
import org.geotools.feature.visitor.MaxVisitor;
import org.geotools.feature.visitor.MinVisitor;
import org.geotools.feature.visitor.NearestVisitor;
import org.geotools.feature.visitor.UniqueVisitor;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.geotools.data.reader.FeatureReaderBuilder;
import org.locationtech.geogig.geotools.data.reader.WalkInfo;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;

/**
 * Helper class for {@link GeogigFeatureSource} to handle a {@link FeatureVisitor} if possible
 * without having to fall back to traversing the feature collection.
 * <p>
 * Currently handles {@link UniqueVisitor unique}, {@link MinVisitor min}, {@link MaxVisitor max},
 * and {@link NearestVisitor nearest} visitors.
 */
class GeogigFeatureVisitorHandler {

    private static class Key {
        final NodeRef featureTypeRef;

        final String propertyName;

        public Key(NodeRef ref, String prop) {
            this.featureTypeRef = ref;
            this.propertyName = prop;
        }

        static Key of(NodeRef ref, String prop) {
            return new Key(ref, prop);
        }

        public @Override boolean equals(Object o) {
            Key k = (Key) o;
            return featureTypeRef.equals(k.featureTypeRef) && propertyName.equals(k.propertyName);
        }

        public @Override int hashCode() {
            return Objects.hash(featureTypeRef, propertyName);
        }
    }

    /**
     * A small cache of unique values {@link SoftReference SoftReference<Set<? extends Comparable>}
     * values, key'ed by a feature type's {@link NodeRef}
     */
    private static Cache<Key, NavigableSet<Object>> uniqueValuesCache = CacheBuilder//
            .newBuilder()//
            .maximumSize(50)//
            .softValues()//
            .build();

    public @VisibleForTesting static void clearCache() {
        GeogigFeatureVisitorHandler.uniqueValuesCache.invalidateAll();
    }

    public boolean handle(FeatureVisitor visitor, Query query, GeogigFeatureSource source) {
        if (visitor instanceof FeatureCalc) {
            boolean handled = acceptFeatureCalc(query.getFilter(), (FeatureCalc) visitor, source);
            return handled;
        }
        return false;
    }

    private boolean acceptFeatureCalc(Filter filter, FeatureCalc visitor,
            GeogigFeatureSource source) {
        if (!(UniqueVisitor.class.isInstance(visitor) || MinVisitor.class.isInstance(visitor)
                || MaxVisitor.class.isInstance(visitor)
                || NearestVisitor.class.isInstance(visitor))) {
            return false;
        }
        final String propertyName;
        {
            Expression exp;
            if (NearestVisitor.class.isInstance(visitor)) {
                // I think it's a mistake that NearestVisitor does not implement
                // FeatureAttributeVisitor like the others do
                exp = ((NearestVisitor) visitor).getExpression();
            } else {
                FeatureAttributeVisitor fav = (FeatureAttributeVisitor) visitor;
                exp = fav.getExpressions().get(0);
            }
            if (!(exp instanceof PropertyName)) {
                return false;
            }
            propertyName = ((PropertyName) exp).getPropertyName();
        }
        NavigableSet<Object> uniqueValues = getUniqueValues(propertyName, filter, source);
        if (null == uniqueValues) {
            return false;
        }
        if (UniqueVisitor.class.isInstance(visitor)) {
            ((UniqueVisitor) visitor).setValue(uniqueValues);
        } else if (MinVisitor.class.isInstance(visitor)) {
            Object lowest = uniqueValues.first();
            ((MinVisitor) visitor).setValue(lowest);
        } else if (MaxVisitor.class.isInstance(visitor)) {
            Object highest = uniqueValues.last();
            ((MaxVisitor) visitor).setValue(highest);
        } else if (NearestVisitor.class.isInstance(visitor)) {
            NearestVisitor ne = (NearestVisitor) visitor;
            Object valueToMatch = ne.getValueToMatch();
            Object maxBelow = uniqueValues.floor(valueToMatch);
            Object minAbove = uniqueValues.ceiling(valueToMatch);
            ne.setValue(maxBelow, minAbove);
        }

        return true;
    }

    private @Nullable NavigableSet<Object> getUniqueValues(String propertyName, Filter filter,
            GeogigFeatureSource source) {

        final NodeRef typeRef = source.getTypeRef();
        final Key key = Key.of(typeRef, propertyName);
        if (Filter.INCLUDE.equals(filter)) {
            NavigableSet<Object> cached = uniqueValuesCache.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
        }

        final Context context = source.getCommandLocator();
        final RevFeatureType nativeType = source.getNativeType();

        final FeatureReaderBuilder builder;
        builder = FeatureReaderBuilder.builder(context, nativeType, typeRef);
        WalkInfo walkInfo = builder//
                .targetSchema(source.getSchema())//
                .filter(filter)//
                .headRef(source.getRootRef())//
                .propertyNames(propertyName)//
                .retypeIfNeeded(false)// don't force retyping, we don't need to return the schema
                                      // matching Query properties
                .buildTreeWalk();

        DiffTree diff = walkInfo.diffOp;
        diff.setPreserveIterationOrder(false);

        final Set<String> materializedIndexProperties = walkInfo.materializedIndexProperties;
        final boolean attributeIsMaterialized = materializedIndexProperties.contains(propertyName);

        final NavigableSet<Object> uniqueValues = new ConcurrentSkipListSet<>();
        final Consumer consumer;

        if (attributeIsMaterialized) {
            consumer = new MaterializedConsumer(propertyName, uniqueValues);
            diff.call(consumer);
        } else {
            final int attributeIndex = findAttributeIndex(propertyName, nativeType);
            ObjectStore store = source.getRepository().objectDatabase();
            try (AutoCloseableIterator<NodeRef> refs = FeatureReaderBuilder
                    .toFeatureRefs(diff.call(), ChangeType.ADDED)) {
                try (AutoCloseableIterator<ObjectInfo<RevFeature>> features = new BulkFeatureRetriever(
                        store, store).getGeoGIGFeatures(refs)) {
                    while (features.hasNext()) {
                        ObjectInfo<RevFeature> obj = features.next();
                        RevFeature revFeature = obj.object();
                        Optional<Object> value = revFeature.get(attributeIndex);
                        if (value.isPresent()) {
                            uniqueValues.add(value.get());
                        }
                    }
                }
            }
        }
        if (Filter.INCLUDE.equals(filter)) {
            uniqueValuesCache.put(key, uniqueValues);
        }

        return uniqueValues;
    }

    private int findAttributeIndex(String propertyName, RevFeatureType nativeType) {
        ArrayList<PropertyDescriptor> descriptors = Lists
                .newArrayList(nativeType.type().getDescriptors());
        for (int i = 0; i < descriptors.size(); i++) {
            if (propertyName.equals(descriptors.get(i).getName().getLocalPart())) {
                return i;
            }
        }
        throw new IllegalArgumentException(
                String.format("Property %s not found in %s", propertyName, nativeType.type()));
    }

    private static class MaterializedConsumer extends PreOrderDiffWalk.AbstractConsumer {
        private final NavigableSet<Object> uniqueValues;

        private final String propertyName;

        public MaterializedConsumer(String propertyName, final NavigableSet<Object> uniqueValues) {
            this.propertyName = propertyName;
            this.uniqueValues = uniqueValues;
        }

        public @Override boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            if (right != null) {
                Node node = right.getNode();
                Object value = IndexInfo.getMaterializedAttribute(propertyName, node);
                if (value != null) {
                    // TODO: support null through a "null object", ConcurrentSkipListSet
                    // does not allow nulls
                    uniqueValues.add(value);
                }
            }
            return true;
        }
    }

}
