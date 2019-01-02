/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readObjectId;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.internal.FilteredDiffIterator;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.storage.datastream.FormatCommonV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.io.CountingOutputStream;

/**
 * Provides a method of packing a set of changes and the affected objects to and from a binary
 * stream.
 */
public final class BinaryPackedChanges {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryPackedChanges.class);

    private static final DataStreamRevObjectSerializerV1 serializer = DataStreamRevObjectSerializerV1.INSTANCE;

    private final Repository repository;

    private boolean filtered;

    private static enum CHUNK_TYPE {
        DIFF_ENTRY {
            @Override
            public int value() {
                return 0;
            }
        },
        OBJECT_AND_DIFF_ENTRY {
            @Override
            public int value() {
                return 1;
            }
        },
        METADATA_OBJECT_AND_DIFF_ENTRY {
            @Override
            public int value() {
                return 2;
            }
        },
        FILTER_FLAG {
            @Override
            public int value() {
                return 3;
            }
        };

        public abstract int value();

        private static final CHUNK_TYPE[] values = CHUNK_TYPE.values();

        public static CHUNK_TYPE valueOf(int value) {
            // abusing the fact that value() coincides with ordinal()
            return values[value];
        }
    };

    /**
     * Constructs a new {@code BinaryPackedChanges} instance using the provided {@link Repository}.
     * 
     * @param repository the repository to save objects to, or read objects from, depending on the
     *        operation
     */
    public BinaryPackedChanges(Repository repository) {
        this.repository = repository;
        filtered = false;
    }

    public boolean wasFiltered() {
        return filtered;
    }

    /**
     * Writes the set of changes to the provided output stream.
     * 
     * @param out the stream to write to
     * @param changes the changes to write
     * @throws IOException
     * @return the number of objects written
     */
    public long write(OutputStream out, Iterator<DiffEntry> changes) throws IOException {
        final ObjectStore objectDatabase = repository.objectDatabase();
        out = new CountingOutputStream(out);

        // avoids sending the same metadata object multiple times
        Set<ObjectId> writtenMetadataIds = new HashSet<ObjectId>();

        // buffer to avoid ObjectId cloning its internal state for each object
        byte[] oidbuffer = new byte[ObjectId.NUM_BYTES];

        long objectCount = 0;

        while (changes.hasNext()) {
            DiffEntry diff = changes.next();

            if (diff.isDelete()) {
                out.write(CHUNK_TYPE.DIFF_ENTRY.value());
            } else {
                // its a change or an addition, new object is guaranteed to be present
                NodeRef newObject = diff.getNewObject();
                ObjectId metadataId = newObject.getMetadataId();
                if (writtenMetadataIds.contains(metadataId)) {
                    out.write(CHUNK_TYPE.OBJECT_AND_DIFF_ENTRY.value());
                } else {
                    out.write(CHUNK_TYPE.METADATA_OBJECT_AND_DIFF_ENTRY.value());
                    RevObject metadata = objectDatabase.get(metadataId);
                    writeObjectId(metadataId, out, oidbuffer);
                    serializer.write(metadata, out);
                    writtenMetadataIds.add(metadataId);
                    objectCount++;
                }

                ObjectId objectId = newObject.getObjectId();
                writeObjectId(objectId, out, oidbuffer);
                RevObject object = objectDatabase.get(objectId);
                serializer.write(object, out);
                objectCount++;
            }
            DataOutputStream dataOut = new DataOutputStream(out);
            FormatCommonV1.writeDiff(diff, dataOut);
            dataOut.flush();
        }
        // signal the end of changes
        out.write(CHUNK_TYPE.FILTER_FLAG.value());
        final boolean filtersApplied = changes instanceof FilteredDiffIterator
                && ((FilteredDiffIterator) changes).wasFiltered();
        out.write(filtersApplied ? 1 : 0);

        LOGGER.info(String.format("Written %,d bytes to remote accounting for %,d objects.",
                ((CountingOutputStream) out).getCount(), objectCount));
        return objectCount;
    }

    private void writeObjectId(ObjectId objectId, OutputStream out, byte[] oidbuffer)
            throws IOException {
        objectId.getRawValue(oidbuffer);
        out.write(oidbuffer);
    }

    /**
     * Read in the changes from the provided input stream and call the provided callback for each
     * change. The input stream represents the output of another {@code BinaryPackedChanges}
     * instance.
     * 
     * @param in the stream to read from
     * @param callback the callback to call for each item
     */
    public void ingest(final InputStream in, Callback callback) {
        PacketReadingIterator readingIterator = new PacketReadingIterator(in);

        Iterator<RevObject> asObjects = asObjects(readingIterator, callback);

        ObjectStore objectDatabase = repository.objectDatabase();
        CountingListener listener = BulkOpListener.newCountingListener();
        objectDatabase.putAll(asObjects, listener);
        LOGGER.info("Ingested %,d objects. Inserted: %,d. Already existing: %,d\n",
                listener.inserted() + listener.found(), listener.inserted(), listener.found());
        this.filtered = readingIterator.isFiltered();
    }

    /**
     * Returns an iterator that calls the {@code callback} for each {@link DiffPacket}'s
     * {@link DiffEntry} once, and returns either zero, one, or two {@link RevObject}s, depending on
     * which information the diff packet carried over.
     */
    private Iterator<RevObject> asObjects(final PacketReadingIterator readingIterator,
            final Callback callback) {
        return new AbstractIterator<RevObject>() {

            private DiffPacket current;

            @Override
            protected RevObject computeNext() {
                if (current != null) {
                    Preconditions.checkState(current.metadataObject != null);
                    RevObject ret = current.metadataObject;
                    current = null;
                    return ret;
                }
                while (readingIterator.hasNext()) {
                    DiffPacket diffPacket = readingIterator.next();
                    callback.callback(diffPacket.entry);
                    RevObject obj = diffPacket.newObject;
                    RevObject md = diffPacket.metadataObject;
                    Preconditions.checkState(obj != null || (obj == null && md == null));
                    if (obj != null) {
                        if (md != null) {
                            current = diffPacket;
                        }
                        return obj;
                    }
                }
                return endOfData();
            }
        };
    }

    private static class DiffPacket {

        public final DiffEntry entry;

        @Nullable
        public final RevObject newObject;

        @Nullable
        public final RevObject metadataObject;

        public DiffPacket(DiffEntry entry, @Nullable RevObject newObject,
                @Nullable RevObject metadata) {
            this.entry = entry;
            this.newObject = newObject;
            this.metadataObject = metadata;
        }
    }

    private static class PacketReadingIterator extends AbstractIterator<DiffPacket> {

        private InputStream in;

        private DataInput data;

        private boolean filtered;

        public PacketReadingIterator(InputStream in) {
            this.in = in;
            this.data = new DataInputStream(in);
        }

        /**
         * @return {@code true} if the stream finished with a non zero "filter applied" marker
         */
        public boolean isFiltered() {
            return filtered;
        }

        @Override
        protected DiffPacket computeNext() {
            try {
                return readNext();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private DiffPacket readNext() throws IOException {
            final CHUNK_TYPE chunkType = CHUNK_TYPE.valueOf((int) (data.readByte() & 0xFF));

            RevObject revObj = null;
            RevObject metadata = null;

            switch (chunkType) {
            case DIFF_ENTRY:
                break;
            case OBJECT_AND_DIFF_ENTRY: {
                ObjectId id = readObjectId(data);
                revObj = serializer.read(id, in);
            }
                break;
            case METADATA_OBJECT_AND_DIFF_ENTRY: {
                ObjectId mdid = readObjectId(data);
                metadata = serializer.read(mdid, in);
                ObjectId id = readObjectId(data);
                revObj = serializer.read(id, in);
            }
                break;
            case FILTER_FLAG: {
                int changesFiltered = in.read();
                if (changesFiltered != 0) {
                    filtered = true;
                }
                return endOfData();
            }
            default:
                throw new IllegalStateException("Unknown chunk type: " + chunkType);
            }

            DiffEntry diff = FormatCommonV1.readDiff(data);
            return new DiffPacket(diff, revObj, metadata);
        }
    }

    /**
     * Interface for callback methods to be used by the read and write operations.
     */
    public static interface Callback {
        public abstract void callback(DiffEntry diff);
    }

}
