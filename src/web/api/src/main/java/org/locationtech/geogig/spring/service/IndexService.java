/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.index.BuildFullHistoryIndexOp;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.DropIndexOp;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.IndexUtils;
import org.locationtech.geogig.porcelain.index.UpdateIndexOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.spring.dto.IndexInfoBean;
import org.locationtech.geogig.spring.dto.IndexList;
import org.locationtech.geogig.spring.dto.RepositoryList;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Internal service for constructing a {@link RepositoryList} DTO for Controllers to consume.
 */
@Service("indexService")
public class IndexService {

    public IndexList getIndexList(Repository repo, @Nullable String treeName) {
        IndexList list = new IndexList();
        final List<IndexInfo> indexInfos;
        if (treeName != null) {
            indexInfos = repo.indexDatabase().getIndexInfos(treeName);
            if (indexInfos.size() == 0) {
                NodeRef treeRef = IndexUtils.resolveTypeTreeRef(repo.context(), treeName);
                if (treeRef == null) {
                    throw new CommandSpecException(
                            "The provided tree name was not found in the HEAD commit.",
                            HttpStatus.NOT_FOUND);
                }
            }
        } else {
            indexInfos = repo.indexDatabase().getIndexInfos();
        }
        for (IndexInfo info : indexInfos) {
            list.addIndex(new IndexInfoBean(info));
        }
        return list;
    }

    public IndexInfoBean createIndex(Repository repo, String treeRefSpec,
            String geometryAttributeName, List<String> extraAttributes, boolean indexHistory,
            String bounds) {
        Envelope bbox = SpatialOps.parseNonReferencedBBOX(bounds);

        final Index index = repo.command(CreateQuadTree.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setGeometryAttributeName(geometryAttributeName)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .setBounds(bbox)//
                .call();

        return new IndexInfoBean(index.info());
    }

    public IndexInfoBean updateIndex(Repository repo, String treeRefSpec,
            String geometryAttributeName, List<String> extraAttributes, boolean indexHistory,
            boolean add, boolean overwrite, String bounds) {
        Envelope bbox = SpatialOps.parseNonReferencedBBOX(bounds);

        final Index index = repo.command(UpdateIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(geometryAttributeName)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .setAdd(add)//
                .setOverwrite(overwrite)//
                .setBounds(bbox)//
                .call();

        return new IndexInfoBean(index.info());
    }

    public Integer rebuildIndex(Repository repo, String treeRefSpec,
            String geometryAttributeName) {
        final int treesRebuilt = repo.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(geometryAttributeName)//
                .call();
        return treesRebuilt;
    }

    public IndexInfoBean dropIndex(Repository repo, String treeRefSpec,
            String geometryAttributeName) {
        final IndexInfo dropped = repo.command(DropIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(geometryAttributeName)//
                .call();

        return new IndexInfoBean(dropped);
    }
}
