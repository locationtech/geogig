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
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;

/**
 * Drops an {@link IndexInfo} and its tree mappings from the index database.
 */
public class DropIndexOp extends AbstractGeoGigOp<IndexInfo> {

    private String treeRefSpec;

    private @Nullable String attributeName;

    /**
     * @param treeRefSpec the tree refspec of the index to be dropped
     * @return {@code this}
     */
    public DropIndexOp setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    /**
     * @param attributeName the attribute name of the index to be dropped
     * @return {@code this}
     */
    public DropIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return The {@link IndexInfo} of the dropped index.
     */
    @Override
    protected IndexInfo _call() {
        checkArgument(treeRefSpec != null, "Tree ref spec not provided.");
        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        checkArgument(typeTreeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(indexDatabase(), treeName,
                attributeName);
        checkState(!indexInfos.isEmpty(), "A matching index could not be found.");
        checkState(indexInfos.size() == 1,
                "Multiple indexes were found for the specified tree, please specify the attribute.");

        IndexInfo indexInfo = indexInfos.get(0);

        boolean dropped = indexDatabase().dropIndex(indexInfo);
        checkState(dropped,
                "Unable to drop the index from the database, it may have already been dropped.");

        return indexInfo;
    }
}
