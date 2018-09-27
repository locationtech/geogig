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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.BuildFullHistoryIndexOp;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Updates an {@link IndexInfo} with new metadata.
 */
public class UpdateIndexOp extends AbstractGeoGigOp<Index> {

    private String treeRefSpec;

    private @Nullable String attributeName;

    private @Nullable List<String> extraAttributes;

    private boolean overwrite = false;

    private boolean add = false;

    private boolean indexHistory = false;

    private Envelope bounds = null;

    /**
     * @param treeRefSpec the tree refspec of the index to be updated
     * @return {@code this}
     */
    public UpdateIndexOp setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    /**
     * @param attributeName the attribute name of the index to be updated
     * @return {@code this}
     */
    public UpdateIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    /**
     * @param extraAttributes the extra attributes for the updated index
     * @return {@code this}
     * 
     * @see #setAdd(boolean)
     * @see #setOverwrite(boolean)
     */
    public UpdateIndexOp setExtraAttributes(List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
        return this;
    }

    /**
     * Overwrite old extra attributes with new ones.
     * 
     * @param overwrite if {@code true}, the old extra attributes will be replaced with the new ones
     * @return {@code this}
     */
    public UpdateIndexOp setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    /**
     * Add new extra attributes to the attributes already being tracked on the index.
     * 
     * @param add if {@code true}, the new extra attributes will be added to the existing ones
     * @return {@code this}
     */
    public UpdateIndexOp setAdd(boolean add) {
        this.add = add;
        return this;
    }

    /**
     * Rebuild the indexes for the full history of the feature tree.
     * 
     * @param indexHistory if {@code true}, the full history of the feature tree will be rebuilt
     * @return {@code this}
     */
    public UpdateIndexOp setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }

    /**
     * Sets the bounds of the spatial index.
     * 
     * @param bounds the {@link Envelope} that represents the bounds of the spatial index
     * @return {@code this}
     */
    public UpdateIndexOp setBounds(Envelope bounds) {
        this.bounds = bounds;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return an {@link Index} that represents the updated index
     */
    @Override
    protected Index _call() {
        checkArgument(treeRefSpec != null, "Tree ref spec not provided.");

        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        checkArgument(typeTreeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        final ObjectDatabase objectDatabase = objectDatabase();
        final RevFeatureType featureType = objectDatabase
                .getFeatureType(typeTreeRef.getMetadataId());
        final String treeName = typeTreeRef.path();
        final IndexDatabase indexDatabase = indexDatabase();
        final List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(indexDatabase(), treeName,
                attributeName);

        checkArgument(!indexInfos.isEmpty(), "A matching index could not be found.");
        checkArgument(indexInfos.size() == 1,
                "Multiple indexes were found for the specified tree, please specify the attribute.");

        IndexInfo oldIndexInfo = indexInfos.get(0);

        final @Nullable String[] newAttributes = IndexUtils
                .resolveMaterializedAttributeNames(featureType, extraAttributes);

        final IndexInfo newIndexInfo;
        Map<String, Object> newMetadata = Maps.newHashMap(oldIndexInfo.getMetadata());
        String[] oldAttributes = (String[]) newMetadata
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        String[] updatedAttributes;
        if (add) {
            if (oldAttributes == null) {
                updatedAttributes = newAttributes;
            } else if (newAttributes == null) {
                updatedAttributes = oldAttributes;
            } else {
                Set<String> oldSet = Sets.newHashSet(oldAttributes);
                Set<String> newSet = Sets.newHashSet(newAttributes);
                oldSet.addAll(newSet);
                updatedAttributes = oldSet.toArray(new String[oldSet.size()]);
            }
        } else if (overwrite) {
            updatedAttributes = newAttributes;
        } else if (newAttributes != null) {
            checkArgument(oldAttributes == null,
                    "Extra attributes already exist on index, specify add or overwrite to update.");
            updatedAttributes = newAttributes;
        } else {
            updatedAttributes = oldAttributes;
        }

        boolean updated = false;
        if (!contentsEqual(updatedAttributes, oldAttributes)) {
            if (updatedAttributes == null) {
                newMetadata.remove(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
            } else {
                newMetadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, updatedAttributes);
            }
            updated = true;
        }

        if (bounds != null) {
            newMetadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, bounds);
            updated = true;
        }

        checkArgument(updated, "Nothing to update...");

        final RevTree canonicalTree = objectDatabase.getTree(typeTreeRef.getObjectId());

        newIndexInfo = indexDatabase.updateIndexInfo(treeName, oldIndexInfo.getAttributeName(),
                oldIndexInfo.getIndexType(), newMetadata);

        ObjectId indexedTreeId;

        final ProgressListener listener = getProgressListener();
        try {
            if (indexHistory) {
                command(BuildFullHistoryIndexOp.class)//
                        .setTreeRefSpec(treeRefSpec)//
                        .setAttributeName(oldIndexInfo.getAttributeName())//
                        .setProgressListener(listener)//
                        .call();
                Optional<ObjectId> headIndexedTreeId = indexDatabase
                        .resolveIndexedTree(newIndexInfo, canonicalTree.getId());
                checkArgument(headIndexedTreeId.isPresent(),
                        "HEAD indexed tree could not be resolved after building history indexes.");
                indexedTreeId = headIndexedTreeId.get();
            } else {
                RevTree indexedTree = command(BuildIndexOp.class)//
                        .setIndex(newIndexInfo)//
                        .setOldCanonicalTree(RevTree.EMPTY)//
                        .setNewCanonicalTree(canonicalTree)//
                        .setRevFeatureTypeId(featureType.getId())//
                        .setProgressListener(listener)//
                        .call();
                if (listener.isCanceled()) {
                    return null;
                }
                indexedTreeId = indexedTree.getId();
            }
        } catch (Exception e) {
            // "rollback"
            indexDatabase.updateIndexInfo(treeName, oldIndexInfo.getAttributeName(),
                    oldIndexInfo.getIndexType(), oldIndexInfo.getMetadata());
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        if (listener.isCanceled()) {
            return null;
        }
        return new Index(newIndexInfo, indexedTreeId, indexDatabase);
    }

    private boolean contentsEqual(@Nullable String[] left, @Nullable String[] right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        Set<String> leftSet = Sets.newHashSet(left);
        Set<String> rightSet = Sets.newHashSet(right);
        return leftSet.containsAll(rightSet) && rightSet.containsAll(leftSet);
    }
}
