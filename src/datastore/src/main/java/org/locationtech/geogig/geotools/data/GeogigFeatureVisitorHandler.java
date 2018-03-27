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
import java.util.NavigableSet;
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
import org.locationtech.geogig.geotools.data.reader.FeatureReaderBuilder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.opengis.feature.FeatureVisitor;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Helper class for {@link GeogigFeatureSource} to handle a {@link FeatureVisitor} if possible
 * without having to fall back to traversing the feature collection.
 * <p>
 * Currently handles {@link UniqueVisitor unique}, {@link MinVisitor min}, {@link MaxVisitor max},
 * and {@link NearestVisitor nearest} visitors.
 */
class GeogigFeatureVisitorHandler {

    /**
     * A small cache of unique values {@link SoftReference SoftReference<Set<? extends Comparable>}
     * values, key'ed by a feature type's {@link NodeRef}
     */
    private static Cache<NodeRef, NavigableSet<Object>> uniqueValuesCache = CacheBuilder//
            .newBuilder()//
            .maximumSize(50)//
            .softValues()//
            .build();

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
        if (Filter.INCLUDE.equals(filter)) {
            NavigableSet<Object> cached = uniqueValuesCache.getIfPresent(typeRef);
            if (cached != null) {
                return cached;
            }
        }

        final Context context = source.getCommandLocator();
        final RevFeatureType nativeType = source.getNativeType();

        final FeatureReaderBuilder builder;
        builder = FeatureReaderBuilder.builder(context, nativeType, typeRef);
        DiffTree diff = builder//
                .targetSchema(source.getSchema())//
                .filter(filter)//
                .headRef(source.getRootRef())//
                .oldHeadRef(source.oldRoot())//
                .changeType(source.changeType())//
                .propertyNames(propertyName)//
                .retypeIfNeeded(false)// don't force retyping, we don't need to return the schema
                                      // matching Query properties
                .buildTreeWalk();

        diff.setPreserveIterationOrder(false);

        final Set<String> materializedIndexProperties = builder.getMaterializedIndexProperties();
        final boolean canHandle = materializedIndexProperties.contains(propertyName);
        if (!canHandle) {
            return null;
        }

        final NavigableSet<Object> uniqueValues = new ConcurrentSkipListSet<>();

        Consumer consumer = new PreOrderDiffWalk.AbstractConsumer() {
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
        };
        diff.call(consumer);

        if (Filter.INCLUDE.equals(filter)) {
            uniqueValuesCache.put(typeRef, uniqueValues);
        }

        return uniqueValues;
    }

}
