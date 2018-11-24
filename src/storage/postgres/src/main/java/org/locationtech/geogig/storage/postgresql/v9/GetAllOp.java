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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.cache.ObjectCache;
import org.locationtech.geogig.storage.postgresql.config.PGId;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;

class GetAllOp<T extends RevObject> implements Callable<List<T>> {

    private final Set<ObjectId> queryIds;

    private final BulkOpListener callback;

    private final PGObjectStore db;

    private final ObjectCache sharedCache;

    private final Class<T> type;

    private final boolean notify;

    public GetAllOp(Collection<ObjectId> ids, BulkOpListener listener, PGObjectStore db,
            Class<T> type) {
        this.queryIds = Sets.newHashSet(ids);
        this.callback = listener;
        this.notify = !BulkOpListener.NOOP_LISTENER.equals(listener);
        this.db = db;
        this.type = type;
        this.sharedCache = db.sharedCache;
    }

    @Override
    public List<T> call() throws Exception {
        checkState(db.isOpen(), "Database is closed");
        final TYPE objType = RevObject.class.equals(type) ? null : RevObject.TYPE.valueOf(type);
        final String tableName = db.tableNameForType(objType, null);

        final int queryCount = queryIds.size();
        List<T> found = new ArrayList<>(queryCount);

        if (tableName != null) {
            byte[] bytes;
            ObjectId id;

            final String sql = format(
                    "SELECT ((id).h1), ((id).h2),((id).h3), object FROM %s WHERE ((id).h1) = ANY(?)",
                    tableName);

            try (Connection cx = PGStorage.newConnection(db.dataSource)) {
                try (PreparedStatement ps = cx
                        .prepareStatement(log(sql, PGObjectStore.LOG, queryIds))) {
                    final Array array = toJDBCArray(cx, queryIds);
                    ps.setFetchSize(queryCount);
                    ps.setArray(1, array);

                    final Stopwatch sw = PGObjectStore.LOG.isTraceEnabled()
                            ? Stopwatch.createStarted()
                            : null;

                    try (ResultSet rs = ps.executeQuery()) {
                        if (PGObjectStore.LOG.isTraceEnabled()) {
                            PGObjectStore.LOG
                                    .trace(String.format("Executed getAll for %,d ids in %,dms",
                                            queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                        }
                        while (rs.next()) {
                            id = PGId.valueOf(rs, 1).toObjectId();
                            // only add those that are in the query set. The resultset may
                            // contain
                            // more due to hash1 clashes
                            if (queryIds.contains(id)) {
                                bytes = rs.getBytes(4);

                                RevObject obj = PGObjectStore.encoder.decode(id, bytes);
                                if (objType == null || objType.equals(obj.getType())) {
                                    if (notify) {
                                        queryIds.remove(id);
                                        callback.found(id, Integer.valueOf(bytes.length));
                                    }
                                    found.add(type.cast(obj));
                                    sharedCache.put(obj);
                                }
                            }
                        }
                    }
                    if (PGObjectStore.LOG.isTraceEnabled()) {
                        sw.stop();
                        PGObjectStore.LOG.trace(String.format(
                                "Finished getAll for %,d out of %,d ids in %,dms", found.size(),
                                queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                    }
                }
            }
        }
        if (notify) {
            for (ObjectId oid : queryIds) {
                callback.notFound(oid);
            }
        }
        return found;
    }

    private Array toJDBCArray(Connection cx, final Collection<ObjectId> queryIds)
            throws SQLException {
        Array array;
        Object[] arr = new Object[queryIds.size()];
        Iterator<ObjectId> it = queryIds.iterator();
        for (int i = 0; it.hasNext(); i++) {
            ObjectId id = it.next();
            arr[i] = Integer.valueOf(PGId.valueOf(id).hash1());
        }
        array = cx.createArrayOf("integer", arr);
        return array;
    }
}