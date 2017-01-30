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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.NodeRef;
import org.opengis.feature.type.GeometryDescriptor;

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

        final NodeRef typeTreeRef = this.typeTreeRef != null ? this.typeTreeRef
                : IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        canonicalTypeTree = objectDatabase().getTree(typeTreeRef.getObjectId());
        featureType = objectDatabase().getFeatureType(typeTreeRef.getMetadataId());

        final GeometryDescriptor geometryAtt = IndexUtils
                .resolveGeometryAttribute(featureType, geometryAttributeName);
        final Envelope maxBounds = IndexUtils.resolveMaxBounds(geometryAtt);
        final @Nullable String[] extraAttributes = IndexUtils
                .resolveMaterializedAttributeNames(featureType, this.extraAttributes);

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

        if (indexHistory) {
            command(BuildFullHistoryIndexOp.class)//
                    .setTreeRefSpec(treeName)//
                    .setAttributeName(attributeName)//
                    .setProgressListener(getProgressListener())//
                    .call();
        }

        return index;
    }
}
