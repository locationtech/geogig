package org.locationtech.geogig.porcelain.index;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.IndexDatabase;

public class Index {

    private final IndexInfo indexInfo;

    private final ObjectId indexTree;

    private final IndexDatabase indexdb;

    Index(IndexInfo indexInfo, ObjectId indexTree, IndexDatabase indexdb) {
        this.indexInfo = indexInfo;
        this.indexTree = indexTree;
        this.indexdb = indexdb;
    }

    public IndexInfo info() {
        return indexInfo;
    }

    public ObjectId indexTreeId() {
        return indexTree;
    }

    public RevTree indexTree() {
        return indexdb.getTree(indexTree);
    }

    @Override
    public String toString() {
        return String.format("Index(%s) %s on %s(%s)", indexTree.toString().substring(0, 8),
                indexInfo.getIndexType(), indexInfo.getTreeName(), indexInfo.getAttributeName());
    }
}
