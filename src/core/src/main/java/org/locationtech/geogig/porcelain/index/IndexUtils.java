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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Function;
import com.google.common.base.Optional;
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

        //  (r) -> r.path()
        Function<NodeRef, String> fn =  new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef r) {
                return r.path();
            }};

        ImmutableMap<String, NodeRef> map = Maps.uniqueIndex(treeRefs, fn);
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
    public static Envelope resolveMaxBounds(GeometryDescriptor geometryDescriptor) {
        final CoordinateReferenceSystem crs = geometryDescriptor.getCoordinateReferenceSystem();
        checkArgument(crs != null, "Property %s does not define a Coordinate Reference System",
                geometryDescriptor.getLocalName());
        Envelope maxBounds;
        try {
            maxBounds = SpatialOps.boundsOf(crs);
        } catch (Exception e) {
            String crsIdentifier;
            try {
                crsIdentifier = CRS.lookupIdentifier(crs, true);
            } catch (FactoryException ex) {
                crsIdentifier = crs.toString();
            }
            throw new IllegalStateException("Error computing bounds for CRS " + crsIdentifier, e);
        }
        checkArgument(maxBounds != null,
                "Unable to resolve the area of validity for the layer's CRS");
        return maxBounds;
    }

    /**
     * Resolves the geometry attribute of a feature type.
     * 
     * @param featureType the feature type
     * @param geometryAttributeName the name of the geometry attribute (optional)
     * @return the {@link GeometryDescriptor} of the geometry attribute
     */
    public static GeometryDescriptor resolveGeometryAttribute(RevFeatureType featureType,
            @Nullable String geometryAttributeName) {
        GeometryDescriptor descriptor;
        if (geometryAttributeName == null) {
            descriptor = featureType.type().getGeometryDescriptor();
            checkArgument(descriptor != null,
                    "FeatureType '%s' does not define a default geometry attribute",
                    featureType.type().getName().getLocalPart());
        } else {
            PropertyDescriptor prop = featureType.type().getDescriptor(geometryAttributeName);
            checkArgument(prop != null, "property %s does not exist", geometryAttributeName);
            checkArgument(prop instanceof GeometryDescriptor,
                    "property %s is not a geometry attribute", geometryAttributeName);
            descriptor = (GeometryDescriptor) prop;
        }
        return descriptor;
    }
}
