/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.FindFeatureTypeTrees;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Utility functions that are shared between indexing commands.
 */
public class IndexUtils {
    /**
     * Resolve a tree refspec into a NodeRef.
     * 
     * @param context the command context
     * @param treeRefSpec the tree refspec
     * @return the {@link NodeRef} that matched the given refspec, or {@code null} if it didn't
     *         exist
     */
    public static NodeRef resolveTypeTreeRef(Context context, String treeRefSpec) {
        checkArgument(treeRefSpec != null, "type tree was not provided");
        final String rootRef;
        final String treePath;
        final int rootSepIndex = treeRefSpec.indexOf(':');
        if (-1 == rootSepIndex) {
            rootRef = "HEAD";
            treePath = treeRefSpec;
        } else {
            rootRef = treeRefSpec.substring(0, rootSepIndex);
            treePath = treeRefSpec.substring(rootSepIndex + 1);
        }
        List<NodeRef> treeRefs = context.command(FindFeatureTypeTrees.class).setRootTreeRef(rootRef)
                .call();
        ImmutableMap<String, NodeRef> map = Maps.uniqueIndex(treeRefs, NodeRef::path);
        NodeRef treeRef = map.get(treePath);
        return treeRef;
    }

    /**
     * Resolves a given tree and attribute name into one or more {@link IndexInfo} objects
     * 
     * @param indexdb the index database
     * @param treeName the name of the feature tree
     * @param attributeName the name of the indexed attribute. If {@code null}, this function will
     *        return the index infos associated with the given tree name
     * @return the resolved {@link IndexInfo} objects
     */
    public static List<IndexInfo> resolveIndexInfo(IndexDatabase indexdb, String treeName,
            @Nullable String attributeName) {
        if (attributeName == null) {
            return indexdb.getIndexInfos(treeName);
        } else {
            Optional<IndexInfo> indexInfoOpt = indexdb.getIndexInfo(treeName, attributeName);
            if (indexInfoOpt.isPresent()) {
                return Lists.newArrayList(indexInfoOpt.get());
            }
        }
        return Lists.newArrayList();
    }

    /**
     * Resolves the given list of extra attributes into an array of materialized attribute names.
     * 
     * @param featureType the feature type
     * @param extraAttributes the extra attributes to materialize
     * @return the materialized attribute names, or {@code null} if there were no extra attributes
     *         provided
     */
    public static @Nullable String[] resolveMaterializedAttributeNames(RevFeatureType featureType,
            @Nullable List<String> extraAttributes) {
        if (extraAttributes == null || extraAttributes.isEmpty()) {
            return null;
        }
        String[] atts = extraAttributes.toArray(new String[extraAttributes.size()]);
        Arrays.sort(atts);
        final FeatureType type = featureType.type();
        final String typeName = type.getName().getLocalPart();
        for (String attname : atts) {
            PropertyDescriptor descriptor = type.getDescriptor(attname);
            checkArgument(null != descriptor, "FeatureType %s does not define attribute '%s'",
                    typeName, attname);
        }
        return atts;
    }

    /**
     * Resolves the maximum bounds of the CRS of a geometry descriptor.
     * 
     * @param geometryDescriptor the geometry descriptor
     * @return the {@link Envelope} with the maximum bounds of the CRS of the geometry descriptor
     */
    public static Envelope resolveMaxBounds(PropertyDescriptor geometryDescriptor) {
        checkArgument(geometryDescriptor.isGeometryDescriptor());
        final CoordinateReferenceSystem crs = geometryDescriptor.coordinateReferenceSystem();
        checkArgument(!crs.isNull(), "Property %s does not define a Coordinate Reference System",
                geometryDescriptor.getLocalName());
        Optional<Envelope> maxBounds = SpatialOps.boundsOf(crs);
        checkArgument(maxBounds.isPresent(),
                "Unable to resolve the area of validity for the layer's CRS");
        Envelope aov = maxBounds.get();
        Envelope rounded = new Envelope(//
                Math.floor(aov.getMinX()), //
                Math.ceil(aov.getMaxX()), //
                Math.floor(aov.getMinY()), //
                Math.ceil(aov.getMaxY()));
        return rounded;
    }

    /**
     * Resolves the geometry attribute of a feature type.
     * 
     * @param featureType the feature type
     * @param geometryAttributeName the name of the geometry attribute (optional)
     * @return the {@link GeometryDescriptor} of the geometry attribute
     */
    public static PropertyDescriptor resolveGeometryAttribute(RevFeatureType featureType,
            @Nullable String geometryAttributeName) {
        PropertyDescriptor descriptor;
        if (geometryAttributeName == null) {
            descriptor = featureType.type().getGeometryDescriptor().orElse(null);
            checkArgument(descriptor != null,
                    "FeatureType '%s' does not define a default geometry attribute",
                    featureType.type().getName().getLocalPart());
        } else {
            PropertyDescriptor prop;
            try {
                prop = featureType.type().getDescriptor(geometryAttributeName);
            } catch (NoSuchElementException e) {
                throw new IllegalArgumentException(
                        String.format("property %s does not exist", geometryAttributeName));
            }
            checkArgument(prop.isGeometryDescriptor(), "property %s is not a geometry attribute",
                    geometryAttributeName);
            descriptor = prop;
        }
        return descriptor;
    }
}
