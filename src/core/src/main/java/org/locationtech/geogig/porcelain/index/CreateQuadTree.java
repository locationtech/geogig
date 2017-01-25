/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
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

/**
 * Creates a {@link RevTree} that represents a quad-tree out of an existing canonical
 * {@link RevTree}.
 *
 */
public class CreateQuadTree extends AbstractGeoGigOp<Index> {

    /**
     * Either typeTreeRef or treeRefSpec must be provided
     */
    private @Nullable NodeRef typeTreeRef;

    /**
     * Either typeTreeRef or treeRefSpec must be provided
     */
    private @Nullable String treeRefSpec;

    private @Nullable List<String> extraAttributes;

    private boolean indexHistory;

    private @Nullable String geometryAttributeName;

    public CreateQuadTree setTypeTreeRef(NodeRef typeTreeRef) {
        this.typeTreeRef = typeTreeRef;
        return this;
    }

    public CreateQuadTree setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    /**
     * Optional, if given, the geometry attribute to create the quadtree index for, otherwise
     * defaults to the feature type's default geometry attribute
     */
    public CreateQuadTree setGeometryAttributeName(String geomAttName) {
        this.geometryAttributeName = geomAttName;
        return this;
    }

    public CreateQuadTree setExtraAttributes(List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
        return this;
    }

    public CreateQuadTree setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }

    @Override
    protected Index _call() {

        final RevTree canonicalTypeTree;
        final RevFeatureType featureType;

        final NodeRef typeTreeRef = resolveTypeTreeRef();
        canonicalTypeTree = objectDatabase().getTree(typeTreeRef.getObjectId());
        featureType = objectDatabase().getFeatureType(typeTreeRef.getMetadataId());

        final GeometryDescriptor geometryAtt = resolveGeometryAttribute(featureType);
        final Envelope maxBounds = resolveMaxBounds(geometryAtt);
        final @Nullable String[] extraAttributes = resolveMaterializedAttributeNames(featureType);

        final String treeName = typeTreeRef.path();
        final String attributeName = geometryAtt.getLocalName();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, maxBounds);
        if (extraAttributes != null) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }

        Index index = command(CreateIndexOp.class)//
                .setTreeName(treeName)//
                .setAttributeName(attributeName)//
                .setCanonicalTypeTree(canonicalTypeTree)//
                .setFeatureTypeId(featureType.getId())//
                .setIndexType(IndexType.QUADTREE)//
                .setMetadata(metadata)//
                .setProgressListener(getProgressListener())//
                .call();

        return index;
    }

    private NodeRef resolveTypeTreeRef() {
        if (this.typeTreeRef != null) {
            return this.typeTreeRef;
        }
        final String treeRefSpec = this.treeRefSpec;
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
        List<NodeRef> treeRefs = command(FindFeatureTypeTrees.class).setRootTreeRef(rootRef).call();
        ImmutableMap<String, NodeRef> map = Maps.uniqueIndex(treeRefs, (r) -> r.path());
        NodeRef treeRef = map.get(treePath);
        checkArgument(treeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        return treeRef;
    }

    private @Nullable String[] resolveMaterializedAttributeNames(RevFeatureType featureType) {
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

    private Envelope resolveMaxBounds(GeometryDescriptor geometryDescriptor) {
        final CoordinateReferenceSystem crs = geometryDescriptor.getCoordinateReferenceSystem();
        checkArgument(crs != null, "Property %s does not defint a Coordinate Reference System",
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

    private GeometryDescriptor resolveGeometryAttribute(RevFeatureType featureType) {
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
