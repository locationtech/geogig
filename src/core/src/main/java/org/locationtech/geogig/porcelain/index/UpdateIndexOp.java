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

import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Updates an {@link IndexInfo} with new metadata.
 */
public class UpdateIndexOp extends AbstractGeoGigOp<IndexInfo> {

    private String treeRefSpec;

    private @Nullable String attributeName;

    private @Nullable List<String> extraAttributes;

    private boolean overwrite = false;

    private boolean add = false;

    private boolean indexHistory = false;

    public UpdateIndexOp setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    public UpdateIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    public UpdateIndexOp setExtraAttributes(List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
        return this;
    }

    public UpdateIndexOp setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public UpdateIndexOp setAdd(boolean add) {
        this.add = add;
        return this;
    }

    public UpdateIndexOp setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }

    @Override
    protected IndexInfo _call() {
        final RevFeatureType featureType;

        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        featureType = objectDatabase().getFeatureType(typeTreeRef.getMetadataId());
        String treeName = typeTreeRef.path();
        final GeometryDescriptor geometryAtt = IndexUtils.resolveGeometryAttribute(featureType,
                attributeName);
        final String geometryAttributeName = geometryAtt.getLocalName();

        Optional<IndexInfo> indexInfo = indexDatabase().getIndexInfo(treeName,
                geometryAttributeName);
        checkState(indexInfo.isPresent(), "A matching index could not be found to update.");
        
        final @Nullable String[] newAttributes = IndexUtils
                .resolveMaterializedAttributeNames(featureType, extraAttributes);

        IndexInfo oldIndexInfo = indexInfo.get();
        IndexInfo newIndexInfo = null;
        Map<String, Object> newMetadata = Maps.newHashMap(oldIndexInfo.getMetadata());
        String[] oldAttributes = (String[]) newMetadata
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        List<String> updatedAttributes;
        if (oldAttributes == null || oldAttributes.length == 0) {
            if (newAttributes == null) {
                updatedAttributes = null;
            } else {
                updatedAttributes = Lists.newArrayList(newAttributes);
            }
        } else {
            checkState(overwrite || add,
                    "Extra attirbutes already exist on index, specify add or overwrite to update.");
            if (overwrite) {
                if (newAttributes == null) {
                    updatedAttributes = null;
                } else {
                    updatedAttributes = Lists.newArrayList(newAttributes);
                }
            } else {
                updatedAttributes = Lists.newArrayList(oldAttributes);
                if (newAttributes != null) {
                    for (int i = 0; i < newAttributes.length; i++) {
                        if (!updatedAttributes.contains(newAttributes[i])) {
                            updatedAttributes.add(newAttributes[i]);
                        }
                    }
                }
            }
        }
        if (updatedAttributes == null) {
            newMetadata.remove(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        } else {
            newMetadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA,
                updatedAttributes.toArray(new String[updatedAttributes.size()]));
        }
        newIndexInfo = indexDatabase().updateIndexInfo(treeName, geometryAttributeName,
                oldIndexInfo.getIndexType(), newMetadata);

        RevTree canonicalTree = objectDatabase().getTree(typeTreeRef.getObjectId());

        if (indexHistory) {
            command(BuildFullHistoryIndexOp.class)//
                    .setTreeRefSpec(treeRefSpec)//
                    .setAttributeName(geometryAttributeName)//
                    .setProgressListener(getProgressListener())//
                    .call();
        } else {
            command(BuildIndexOp.class)//
                    .setIndex(newIndexInfo)//
                    .setOldCanonicalTree(RevTree.EMPTY)//
                    .setNewCanonicalTree(canonicalTree)//
                    .setRevFeatureTypeId(featureType.getId())//
                    .setProgressListener(getProgressListener())//
                    .call();
        }

        return newIndexInfo;
    }
}
