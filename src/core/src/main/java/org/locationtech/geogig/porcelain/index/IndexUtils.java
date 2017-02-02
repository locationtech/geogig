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
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

public class IndexUtils {
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
        ImmutableMap<String, NodeRef> map = Maps.uniqueIndex(treeRefs, (r) -> r.path());
        NodeRef treeRef = map.get(treePath);
        checkArgument(treeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        return treeRef;
    }

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
        System.err.println("Extra attributes: " + Arrays.toString(atts));
        return atts;
    }

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
