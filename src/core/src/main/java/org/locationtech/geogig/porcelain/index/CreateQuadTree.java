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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.GeometryDescriptor;

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

    private @Nullable Envelope bounds;

    /**
     * @param typeTreeRef the {@link NodeRef} of the canonical tree to build a quadtree from
     * @return {@code this}
     */
    public CreateQuadTree setTypeTreeRef(NodeRef typeTreeRef) {
        this.typeTreeRef = typeTreeRef;
        return this;
    }

    /**
     * @param treeRefSpec the refspec of the tree to build a quadtree from
     * @return {@code this}
     */
    public CreateQuadTree setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    /**
     * Optional, if given, the geometry attribute to create the quadtree index for, otherwise
     * defaults to the feature type's default geometry attribute
     * 
     * @param geometryAttributeName the name of the geometry attribute
     * @return {@code this}
     */
    public CreateQuadTree setGeometryAttributeName(String geometryAttributeName) {
        this.geometryAttributeName = geometryAttributeName;
        return this;
    }

    /**
     * @param extraAttributes extra attributes to keep track of in the indexed tree
     * @return {@code this}
     */
    public CreateQuadTree setExtraAttributes(@Nullable List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
        return this;
    }

    /**
     * Build the indexes for the full history of the feature tree.
     * 
     * @param indexHistory if {@code true}, the full history of the feature tree will be built
     * @return {@code this}
     */
    public CreateQuadTree setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }

    /**
     * Sets the bounds of the quad tree.
     * 
     * @param bounds the {@link Envelope} that represents the bounds of the quad tree
     * @return {@code this}
     */
    public CreateQuadTree setBounds(Envelope bounds) {
        this.bounds = bounds;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return an {@link Index} that represents the newly created index
     */
    @Override
    protected Index _call() {
        checkArgument(typeTreeRef != null || treeRefSpec != null, "No tree was provided.");

        final RevTree canonicalTypeTree;
        final RevFeatureType featureType;

        final NodeRef typeTreeRef = this.typeTreeRef != null ? this.typeTreeRef
                : IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        checkArgument(typeTreeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        canonicalTypeTree = objectDatabase().getTree(typeTreeRef.getObjectId());
        featureType = objectDatabase().getFeatureType(typeTreeRef.getMetadataId());

        final GeometryDescriptor geometryAtt = IndexUtils
                .resolveGeometryAttribute(featureType, geometryAttributeName);
        final Envelope maxBounds = this.bounds != null ? this.bounds
                : IndexUtils.resolveMaxBounds(geometryAtt);
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
                .setIndexHistory(indexHistory)//
                .setProgressListener(getProgressListener())//
                .call();

        return index;
    }
}
