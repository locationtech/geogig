/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import org.geotools.filter.expression.PropertyAccessorFactory;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.opengis.filter.Filter;
import org.opengis.filter.spatial.BinarySpatialOperator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * Adapts a GeoTools {@link Filter} to a {@link Predicate} to be applied over a {@link Bounded}
 * object (i.e. {@link Node}, {@link NodeRef}, or {@link Bucket}).
 * <p>
 * The evaluation returns {@code true} for buckets, so that the tree traversal continues down the
 * path of the bucke to the leaf nodes.
 * <p>
 * Otherwise relies on the GeoTools {@link PropertyAccessorFactory} SPI to pick up the custom
 * {@link ExtraDataPropertyAccessorFactory} in order to resolve attribute values out of a
 * {@link Node} or {@link NodeRef}'s node {@link Node#getExtraData() extra data} map.
 *
 */
final class PreFilter implements Predicate<Bounded> {

    @VisibleForTesting
    final Filter filter;

    public PreFilter(final Filter filter) {
        this.filter = filter;
    }

    @Override
    public boolean apply(Bounded bounded) {
        if (bounded == null) {
            return false;
        }
        // buckets are evaluated to true for the traversal to continue to their leaf nodes, except
        // for spatial filters, which are pre-filtered using the bounding box
        if (bounded instanceof Bucket && !(filter instanceof BinarySpatialOperator)) {
            return true;
        }
        if (bounded instanceof NodeRef && ((NodeRef) bounded).getType() == TYPE.TREE) {
            return true;
        }
        return filter.evaluate(bounded);
    }

    @Override
    public String toString() {
        return String.format("PreFilter(%s)", filter);
    }

    public static Predicate<Bounded> forFilter(Filter filter) {
        if (Filter.INCLUDE.equals(filter)) {
            return Predicates.alwaysTrue();
        }
        if (Filter.EXCLUDE.equals(filter)) {
            return Predicates.alwaysFalse();
        }
        return new PreFilter(filter);
    }
}