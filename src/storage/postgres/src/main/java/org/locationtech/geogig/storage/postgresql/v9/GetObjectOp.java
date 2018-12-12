package org.locationtech.geogig.storage.postgresql.v9;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.cache.ObjectCache;
import org.locationtech.geogig.storage.postgresql.config.PGId;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

import com.google.common.collect.Sets;

class GetObjectOp<T extends RevObject> implements Callable<List<ObjectInfo<T>>> {

    private final Set<NodeRef> queryNodes;

    private final BulkOpListener callback;

    private final PGObjectStore db;

    private final ObjectCache sharedCache;

    private final Class<T> type;

    public GetObjectOp(Collection<NodeRef> ids, BulkOpListener listener, PGObjectStore db,
            Class<T> type) {
        this.queryNodes = Sets.newHashSet(ids);
        this.callback = listener;
        this.db = db;
        this.type = type;
        this.sharedCache = db.sharedCache;
    }

    @Override
    public List<ObjectInfo<T>> call() throws Exception {

        checkState(db.isOpen(), "Database is closed");
        final TYPE objType = RevObject.class.equals(type) ? null : RevObject.TYPE.valueOf(type);
        final String tableName = db.tableNameForType(objType, null);

        final int queryCount = queryNodes.size();

        final String sql = format(
                "SELECT ((id).h1), ((id).h2),((id).h3), object FROM %s WHERE ((id).h1) = ANY(?)",
                tableName);

        Map<ObjectId, byte[]> queryMatches = new HashMap<>();

        try (Connection cx = PGStorage.newConnection(db.dataSource)) {

            final Array array = toJDBCArray(cx, queryNodes);

            try (PreparedStatement ps = cx
                    .prepareStatement(log(sql, PGObjectStore.LOG, queryNodes))) {
                ps.setFetchSize(queryCount);
                ps.setArray(1, array);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectId id = PGId.valueOf(rs, 1).toObjectId();
                        byte[] bytes = rs.getBytes(4);
                        queryMatches.put(id, bytes);
                    }
                }
            }
        }

        List<ObjectInfo<T>> getObjectOpResult = new ArrayList<>(queryCount);
        for (NodeRef n : queryNodes) {
            ObjectId id = n.getObjectId();
            byte[] bytes = queryMatches.get(id);
            if (bytes == null) {
                callback.notFound(n.getObjectId());
            } else {
                RevObject obj = PGObjectStore.encoder.decode(id, bytes);
                if (objType == null || objType.equals(obj.getType())) {
                    callback.found(id, null/* this arg should be deprecated */);
                    ObjectInfo<T> info = ObjectInfo.of(n, type.cast(obj));
                    getObjectOpResult.add(info);
                    sharedCache.put(obj);
                } else {
                    callback.notFound(n.getObjectId());
                }
            }
        }

        return getObjectOpResult;
    }

    private Array toJDBCArray(Connection cx, final Collection<NodeRef> queryIds)
            throws SQLException {
        Array array;
        Object[] arr = new Object[queryIds.size()];
        Iterator<NodeRef> it = queryIds.iterator();
        for (int i = 0; it.hasNext(); i++) {
            ObjectId id = it.next().getObjectId();
            arr[i] = Integer.valueOf(PGId.valueOf(id).hash1());
        }
        array = cx.createArrayOf("integer", arr);
        return array;
    }
}