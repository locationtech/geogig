/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.Map;

import org.locationtech.jts.geom.Geometry;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A {@code RevFeature} is an immutable data structure that contains the attribute value instances
 * of a GIS feature, in the order defined by the {@link RevFeatureType} it was created for, although
 * the {@code RevFeature} instance itself holds absolutely no reference to a {@code RevFeatureType}.
 * <p>
 * A {@code RevFeature} is a leaf object in the revision graph (
 * {@code commit [1]-> tree [0..N]-> tree 
 * <FeatureType> [0..N]-> feature}).
 * <p>
 * It's {@link #getValues() values} list contain plain data type value instances, which are of one
 * of the supported types as defined in the {@link FieldType} enum.
 * <p>
 * The {@code RevFeature} has no reference to the {@link RevFeatureType} it's sequence of values
 * obeys, since it's a pure value data object.
 * <p>
 * When traversing a revision graph, a "feature {@link RevTree tree}" is pointed out by a root
 * object, and the {@link Node} pointing to the feature tree that points to the {@code RevFeature}
 * has it's {@link Node#getMetadataId()} set to the "feature tree" {@code RevFeatureType}. That is,
 * the tree node for the tree that contains the feature has the "layer's" default feature type.
 * <p>
 * A feature {@link Node} pointed out by a "feature tree" (a.k.a. Feature Class, or Layer, in
 * traditional GIS systems), should have its {@link Node#getMetadataId()} unset if it complies to
 * the feature tree's default feature type, and shall have the node's alternate
 * {@code RevFeatureType} id if it doesn't comply to the tree's default feature type.
 * 
 * @implNote Since a {@code RevFeature} shall be immutable and some of it's internal value instances
 *           may be of a mutable type (such as {@link Map} or {@link Geometry}), implementations are
 *           strongly advised to use {@link FieldType#safeCopy(Object)} to return a safe copy of any
 *           possibly mutable internal values.
 * 
 * @since 1.0
 */
public interface RevFeature extends RevObject, ValueArray {

    /**
     * @return a list of values, with {@link Optional#absent()} representing a null value
     */
    public ImmutableList<Optional<Object>> getValues();

    public static RevFeatureBuilder builder() {
        return new RevFeatureBuilder();
    }
}