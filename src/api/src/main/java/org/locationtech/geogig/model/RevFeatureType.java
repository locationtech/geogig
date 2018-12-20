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

import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.ImmutableList;

/**
 * {@code RevFeatureType} is an immutable data structure that describes the schema for a set of
 * {@link RevFeature features}, generally being referred to by the {@link Node#getMetadataId()
 * metadataId} of a {@link RevTree tree} node that points to a tree holding an homogeneous dataset (
 * "feature tree").
 * <p>
 * {@code RevFeatureType} define a set of properties of a certain type of feature instances (that
 * is, a list of property names and value types). The {@link #getId() id} of a
 * {@code RevFeatureType} is used as the {@link Node#getMetadataId() metadataId} for nodes contained
 * in {@link RevTree}s, in order to determine which schema a given feature complies to.
 * <p>
 * In traditional GIS systems, layers (a.k.a. FeatureClasses, or FeatureTypes) represent a dataset
 * whose features share a common schema, such as property names and types, including geometry type.
 * <p>
 * In GeoGig, a FeatureClass or Layer is known as a "feature tree", as it's a {@link RevTree
 * revision tree} that groups a set of {@link RevFeature feature}s. By general usage, all the
 * features directly addressable by a "feature tree" share a common schema, which is determined by
 * the {@link Node#getMetadataId() metadataId} in the node from the feature tree's parent tree
 * (generally a root tree).
 * <p>
 * By specification, all the feature tree {@link Node} objects that point to features that comply to
 * the tree's default metadata id have their {@link Node#getMetadataId()} unset (i.e.
 * {@code Optional.absent()}).
 * <p>
 * GeoGig's object model, however, allows datasets to be structured such that there can be nested
 * feature trees (for example, the {@code roads} feature tree may contain feature nodes as well as
 * nested feature tree nodes such as {@code roads/highways} and {@code roads/interstate}, which may
 * have a different schema than their parent's.
 * <p>
 * More over, GeoGig's object model allows feature trees to contain feature node pointers to
 * features that do not comply to their parent tree schema. In such cases, by specification such
 * {@link Node}s shall have their {@link Node#getMetadataId() metatadaId} set to the
 * {@link ObjectId} of the actual {@code RevFeatureType} to which the feature complies.
 * <p>
 * Note {@link RevFeature} instances are pure value objects and hold no reference to the schema or
 * {@code RevFeatureType} to which they comply. Instead, that information is to be retrieved by
 * traversing the {@link RevTree tree} that holds the {@code Node} pointers, resolving each
 * {@code RevFeature} identifier from the node's {@link Node#getObjectId() objectId}, and the schema
 * they comply to from the feature node's {@link Node#getMetadataId() metadataId}, defaulting to the
 * tree node's {@link Node#getMetadataId() metadataId} when unset.
 * 
 * @since 1.0
 */
public interface RevFeatureType extends RevObject {

    public abstract FeatureType type();

    /**
     * The list of property definitions for this FeatureType
     * 
     * @return the {@link PropertyDescriptor}s of the feature type
     */
    public abstract ImmutableList<PropertyDescriptor> descriptors();

    /**
     * @return the name of the feature type
     */
    public abstract Name getName();

    public static RevFeatureTypeBuilder builder() {
        return new RevFeatureTypeBuilder();
    }
}