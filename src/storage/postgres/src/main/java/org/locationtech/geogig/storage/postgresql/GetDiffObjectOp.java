package org.locationtech.geogig.storage.postgresql;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;

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
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.cache.ObjectCache;

class GetDiffObjectOp<T extends RevObject> implements Callable<List<DiffObjectInfo<T>>> {

    private final List<DiffEntry> queryNodes;

    private final PGObjectStore db;

    private final ObjectCache sharedCache;

    private final Class<T> type;

    public GetDiffObjectOp(Collection<DiffEntry> entries, PGObjectStore db, Class<T> type) {
        this.queryNodes = new ArrayList<>(entries);
        this.db = db;
        this.type = type;
        this.sharedCache = db.sharedCache;
    }

    public @Override List<DiffObjectInfo<T>> call() throws Exception {

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

        List<DiffObjectInfo<T>> result = new ArrayList<>(queryCount);
        for (DiffEntry n : queryNodes) {
            ObjectId leftId = n.oldObjectId();
            ObjectId rightId = n.newObjectId();
            T left = getObject(leftId, queryMatches);
            T right = getObject(rightId, queryMatches);
            DiffObjectInfo<T> diffValues = DiffObjectInfo.of(n, left, right);
            result.add(diffValues);
        }
        return result;
    }

    private @Nullable T getObject(ObjectId id, Map<ObjectId, byte[]> queryMatches) {
        byte[] bytes = queryMatches.get(id);
        if (bytes == null) {
            return null;
        }
        RevObject obj = PGObjectStore.encoder.decode(id, bytes);
        if (!type.isInstance(obj)) {
            throw new IllegalArgumentException(
                    String.format("Requested object %s of type %s its actual type is %s", id, type,
                            obj.getType()));
        }
        return type.cast(obj);
    }

    private Array toJDBCArray(Connection cx, final Collection<DiffEntry> queryIds)
            throws SQLException {

        Set<ObjectId> objectIds = new TreeSet<>();
        for (DiffEntry e : queryIds) {
            if (!e.oldObjectId().isNull()) {
                objectIds.add(e.oldObjectId());
            }
            if (!e.newObjectId().isNull()) {
                objectIds.add(e.newObjectId());
            }
        }

        Array array;
        Object[] arr = new Object[objectIds.size()];
        Iterator<ObjectId> it = objectIds.iterator();
        for (int i = 0; it.hasNext(); i++) {
            ObjectId id = it.next();
            arr[i] = Integer.valueOf(PGId.valueOf(id).hash1());
        }
        array = cx.createArrayOf("integer", arr);
        return array;
    }
}