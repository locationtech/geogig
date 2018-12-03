/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class RocksdbConflictsDatabase implements ConflictsDatabase, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(RocksdbConflictsDatabase.class);

    private final File baseDirectory;

    private static final String NULL_TX_ID = ".default";

    private ConcurrentMap<String/* TxID */, DBHandle> dbsByTransaction = new ConcurrentHashMap<>();

    public @Inject RocksdbConflictsDatabase(Platform platform, @Nullable Hints hints) {
        checkNotNull(platform);
        Optional<URI> repoUriOpt = new ResolveGeogigURI(platform, hints).call();
        checkArgument(repoUriOpt.isPresent(), "couldn't resolve geogig directory");
        URI uri = repoUriOpt.get();
        checkArgument("file".equals(uri.getScheme()));
        this.baseDirectory = new File(new File(uri), "conflicts");
        if (baseDirectory.exists()) {
            checkArgument(baseDirectory.isDirectory() && baseDirectory.canWrite());
        } else {
            checkState(baseDirectory.mkdir(),
                    "unable to create " + baseDirectory.getAbsolutePath());
        }
    }

    @VisibleForTesting
    public RocksdbConflictsDatabase(File baseDirectory) {
        checkNotNull(baseDirectory);
        checkArgument(baseDirectory.exists() && baseDirectory.canWrite());
        this.baseDirectory = baseDirectory;
    }

    public @Override synchronized void open() {
        // no-op
    }

    public @Override synchronized void close() {
        try {
            for (DBHandle db : dbsByTransaction.values()) {
                RocksConnectionManager.INSTANCE.release(db);
            }
        } finally {
            dbsByTransaction.clear();
        }
    }

    private boolean dbExists(@Nullable String txId) {
        final String id = txId == null ? NULL_TX_ID : txId;
        DBHandle dbHandle = dbsByTransaction.get(id);
        if (dbHandle == null) {
            if (!RocksConnectionManager.INSTANCE.exists(dbPath(txId))) {
                return false;
            }
        }
        return true;
    }

    private Optional<RocksDBReference> getDb(@Nullable String txId) {
        final String id = txId == null ? NULL_TX_ID : txId;
        DBHandle dbHandle = dbsByTransaction.get(id);
        if (dbHandle == null) {
            if (RocksConnectionManager.INSTANCE.exists(dbPath(txId))) {
                return Optional.of(getOrCreateDb(txId));
            } else {
                return Optional.absent();
            }
        }
        return Optional.of(dbHandle.getReference());
    }

    private RocksDBReference getOrCreateDb(@Nullable String txId) {
        final String id = txId == null ? NULL_TX_ID : txId;
        DBHandle dbHandle = dbsByTransaction.get(id);
        if (dbHandle == null) {
            String dbpath = dbPath(txId);
            DBConfig address = new DBConfig(dbpath, false);
            dbHandle = RocksConnectionManager.INSTANCE.acquire(address);
            this.dbsByTransaction.put(id, dbHandle);
        }
        return dbHandle.getReference();
    }

    private String dbPath(@Nullable String txId) {
        String dbname = txId == null ? NULL_TX_ID : "." + txId;
        return new File(this.baseDirectory, dbname).getAbsolutePath();
    }

    @Override
    public void removeConflicts(@Nullable String txId) {
        if (dbExists(txId)) {
            String hanldeId = txId == null ? NULL_TX_ID : txId;
            DBHandle dbHandle = this.dbsByTransaction.remove(hanldeId);
            Preconditions.checkNotNull(dbHandle);
            final String dbPath = dbPath(txId);
            final boolean lastHandle = RocksConnectionManager.INSTANCE.release(dbHandle);
            if (lastHandle) {
                // ok, we can just remove the db, there are no more users
                File dbdir = new File(dbPath);
                try {
                    deleteRecustive(dbdir);
                } catch (Exception e) {
                    LOG.error("Error deleting conflicts databse at " + dbPath, e);
                }
            } else {
                // bad luck, lets empty it the hard way
                removeByPrefix(txId, null);
            }
        }
    }

    private void deleteRecustive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File f : children) {
                    deleteRecustive(f);
                }
            }
        }
        file.delete();
    }

    private byte[] key(String path) {
        return path.getBytes(Charsets.UTF_8);
    }

    @Nullable
    private Conflict getInternal(RocksDB db, String path) {
        Conflict c = null;
        byte[] bs;
        try {
            bs = db.get(key(path));
            if (bs != null) {
                c = new ConflictSerializer().read(bs);
            }
        } catch (RocksDBException | IOException e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    @Override
    public boolean hasConflicts(@Nullable String txId) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        boolean hasConflicts = false;
        if (dbRefOpt.isPresent()) {
            try (RocksDBReference dbRef = dbRefOpt.get()) {
                try (RocksIterator it = dbRef.db().newIterator()) {
                    it.seekToFirst();
                    hasConflicts = it.isValid();
                }
            }
        }
        return hasConflicts;
    }

    @Override
    public Optional<Conflict> getConflict(@Nullable String txId, String path) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        Conflict c = null;
        if (dbRefOpt.isPresent()) {
            try (RocksDBReference dbRef = dbRefOpt.get()) {
                c = getInternal(dbRef.db(), path);
            }
        }
        return Optional.fromNullable(c);
    }

    @Override
    public Iterator<Conflict> getByPrefix(@Nullable String txId, @Nullable String prefixFilter) {
        return new BatchIterator(this, txId, prefixFilter);
    }

    @Override
    public long getCountByPrefix(@Nullable String txId, @Nullable String treePath) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        if (!dbRefOpt.isPresent()) {
            return 0L;
        }
        long count = 0;
        try (RocksDBReference dbRef = dbRefOpt.get()) {
            try (RocksIterator it = dbRef.db().newIterator()) {
                byte[] prefixKey = null;
                if (treePath == null) {
                    it.seekToFirst();
                } else {
                    byte[] treeKey = key(treePath);
                    prefixKey = key(treePath + "/");
                    it.seek(treeKey);
                    if (it.isValid() && Arrays.equals(treeKey, it.key())) {
                        count++;
                    }
                    it.seek(prefixKey);
                }
                while (it.isValid() && isPrefix(prefixKey, it.key())) {
                    count++;
                    it.next();
                }
            }
        }
        return count;
    }

    private boolean isPrefix(@Nullable byte[] prefix, byte[] key) {
        if (prefix == null) {
            return true;
        }
        if (prefix.length > key.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != key[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void addConflict(@Nullable String txId, Conflict conflict) {
        addConflicts(txId, Collections.singleton(conflict));
    }

    @Override
    public void addConflicts(@Nullable String txId, Iterable<Conflict> conflicts) {
        try (RocksDBReference dbRef = getOrCreateDb(txId)) {
            ConflictSerializer serializer = new ConflictSerializer();
            try (WriteBatch batch = new WriteBatch()) {
                for (Conflict c : conflicts) {
                    byte[] key = key(c.getPath());
                    byte[] value = serializer.write(c);
                    batch.put(key, value);
                }
                try (WriteOptions writeOptions = new WriteOptions()) {
                    writeOptions.setSync(true);
                    dbRef.db().write(writeOptions, batch);
                }
            } catch (RocksDBException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void removeConflict(@Nullable String txId, String path) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        if (!dbRefOpt.isPresent()) {
            return;
        }
        try (RocksDBReference dbRef = dbRefOpt.get()) {
            dbRef.db().delete(key(path));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeConflicts(@Nullable String txId, Iterable<String> paths) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        if (!dbRefOpt.isPresent()) {
            return;
        }
        try (RocksDBReference dbRef = dbRefOpt.get();
                WriteOptions writeOptions = new WriteOptions();
                WriteBatch batch = new WriteBatch()) {
            writeOptions.setSync(true);
            for (String path : paths) {
                batch.remove(key(path));
            }
            dbRef.db().write(writeOptions, batch);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> findConflicts(@Nullable String txId, Set<String> paths) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        if (!dbRefOpt.isPresent()) {
            return ImmutableSet.of();
        }
        Set<String> found = new HashSet<>();
        byte[] noData = new byte[0];
        try (RocksDBReference dbRef = dbRefOpt.get()) {
            for (String path : paths) {
                int size = dbRef.db().get(key(path), noData);
                if (size > 0) {
                    found.add(path);
                }
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return found;
    }

    @Override
    public void removeByPrefix(@Nullable String txId, @Nullable String pathPrefix) {
        Optional<RocksDBReference> dbRefOpt = getDb(txId);
        if (!dbRefOpt.isPresent()) {
            return;
        }

        final @Nullable byte[] prefix = pathPrefix == null ? null : key(pathPrefix + "/");
        try (RocksDBReference dbRef = dbRefOpt.get(); //
                WriteOptions opts = new WriteOptions(); //
                WriteBatch batch = new WriteBatch()) {
            opts.setSync(true);
            if (pathPrefix != null) {
                batch.remove(key(pathPrefix));
            }
            try (RocksIterator it = dbRef.db().newIterator()) {
                if (prefix == null) {
                    it.seekToFirst();
                } else {
                    it.seek(prefix);
                }
                while (it.isValid()) {
                    byte[] key = it.key();
                    if (isPrefix(prefix, key)) {
                        batch.remove(key);
                    } else {
                        break;
                    }
                    it.next();
                }
            }
            dbRef.db().write(opts, batch);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

    }

    private static class BatchIterator extends AbstractIterator<Conflict> {

        private static final int BATCH_SIZE = 1000;

        private String txId;

        private RocksdbConflictsDatabase rocksConflicts;

        private String prefixFilter;

        private byte[] lastMatchKey;

        private boolean reachedEnd;

        private Iterator<Conflict> currentBatch;

        private final ConflictSerializer serializer = new ConflictSerializer();

        public BatchIterator(RocksdbConflictsDatabase rocksConflicts, @Nullable final String txId,
                @Nullable final String prefixFilter) {
            this.rocksConflicts = rocksConflicts;
            this.txId = txId;
            this.prefixFilter = prefixFilter;
            this.currentBatch = Collections.emptyIterator();

            if (prefixFilter != null) {
                Optional<RocksDBReference> txdb = rocksConflicts.getDb(txId);
                if (txdb.isPresent()) {
                    // get an exact prefix match to account for a conflict on the
                    // tree itself rather than its children
                    byte[] key = rocksConflicts.key(prefixFilter);
                    byte[] treeConflict;
                    try (RocksDBReference dbRef = txdb.get()) {
                        treeConflict = dbRef.db().get(key);
                        if (treeConflict != null) {
                            this.currentBatch = Iterators
                                    .singletonIterator(serializer.read(treeConflict));
                        }
                    } catch (RocksDBException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }

        @Override
        protected Conflict computeNext() {
            if (currentBatch.hasNext()) {
                return currentBatch.next();
            }
            this.currentBatch = nextBatch();
            if (this.currentBatch == null) {
                return endOfData();
            }
            return computeNext();
        }

        @Nullable
        private Iterator<Conflict> nextBatch() {
            if (reachedEnd) {
                return null;
            }
            Optional<RocksDBReference> txdb = rocksConflicts.getDb(txId);
            if (!txdb.isPresent()) {
                return null;
            }

            List<Conflict> conflicts = new ArrayList<>(BATCH_SIZE);

            try (RocksDBReference dbRef = txdb.get()) {
                byte[] keyPrefix = keyPrefix(this.prefixFilter);

                try (RocksIterator rocksit = dbRef.db().newIterator()) {
                    if (lastMatchKey == null) {
                        rocksit.seek(keyPrefix);
                    } else {
                        rocksit.seek(lastMatchKey);
                        // position at the next past last
                        if (rocksit.isValid()) {
                            rocksit.next();
                        }
                    }

                    while (rocksit.isValid() && conflicts.size() < BATCH_SIZE) {
                        byte[] key = rocksit.key();
                        if (isPrefix(keyPrefix, key)) {
                            lastMatchKey = key;
                            byte[] encoded = rocksit.value();
                            conflicts.add(serializer.read(encoded));
                        } else {
                            reachedEnd = true;
                            break;
                        }
                        rocksit.next();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return conflicts.isEmpty() ? null : conflicts.iterator();
        }

        private boolean isPrefix(byte[] keyPrefix, byte[] key) {
            if (key.length < keyPrefix.length) {
                return false;
            }
            for (int i = 0; i < keyPrefix.length; i++) {
                if (keyPrefix[i] != key[i]) {
                    return false;
                }
            }
            return true;
        }

        private byte[] keyPrefix(@Nullable String prefixFilter) {
            if (null == prefixFilter) {
                return new byte[0];
            }
            if (!prefixFilter.endsWith("/")) {
                prefixFilter = prefixFilter + "/";
            }

            return this.rocksConflicts.key(prefixFilter);
        }

    }

    static class ConflictSerializer {

        private static final byte HAS_ANCESTOR = 0b00000001;

        private static final byte HAS_OURS = 0b00000010;

        private static final byte HAS_THEIRS = 0b00000100;

        void write(DataOutput out, ObjectId value) throws IOException {
            value.writeTo(out);
        }

        public byte[] write(Conflict c) throws IOException {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            write(out, c);
            return out.toByteArray();
        }

        ObjectId readId(DataInput in) throws IOException {
            return ObjectId.readFrom(in);
        }

        public void write(DataOutput out, Conflict value) throws IOException {

            String path = value.getPath();
            ObjectId ancestor = value.getAncestor();
            ObjectId ours = value.getOurs();
            ObjectId theirs = value.getTheirs();

            byte flags = ancestor.isNull() ? 0x00 : HAS_ANCESTOR;
            flags |= ours.isNull() ? 0x00 : HAS_OURS;
            flags |= theirs.isNull() ? 0x00 : HAS_THEIRS;

            out.writeByte(flags);
            out.writeUTF(path);
            if (!ancestor.isNull()) {
                write(out, ancestor);
            }
            if (!ours.isNull()) {
                write(out, ours);
            }
            if (!theirs.isNull()) {
                write(out, theirs);
            }
        }

        public Conflict read(byte[] bs) throws IOException {
            return read(ByteStreams.newDataInput(bs));
        }

        public Conflict read(DataInput in) throws IOException {
            byte flags = in.readByte();
            boolean hasAncestor = (flags & HAS_ANCESTOR) == HAS_ANCESTOR;
            boolean hasOurs = (flags & HAS_OURS) == HAS_OURS;
            boolean hasTheirs = (flags & HAS_THEIRS) == HAS_THEIRS;
            String path = in.readUTF();
            ObjectId ancestor = hasAncestor ? readId(in) : ObjectId.NULL;
            ObjectId ours = hasOurs ? readId(in) : ObjectId.NULL;
            ObjectId theirs = hasTheirs ? readId(in) : ObjectId.NULL;
            return new Conflict(path, ancestor, ours, theirs);
        }
    }

}
