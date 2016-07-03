/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - factored out from MergeOp
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.plumbing.ResolveBranchId;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.PersistedIterable;
import org.locationtech.geogig.storage.datastream.FormatCommonV2;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class MergeStatusBuilder extends MergeScenarioConsumer {

    /**
     * Makes for a {@link #conflictsBuffer} of ~14MB
     */
    private final static int BUFFER_SIZE = 100_000;

    final List<Conflict> conflictsBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);

    final PersistedIterable<DiffEntry> diffEntryBuffer = new PersistedIterable<>(null,
            new DiffEntrySerializer(), BUFFER_SIZE, true);

    static final int maxReportedConflicts = 25;

    final AtomicInteger reportedConflicts = new AtomicInteger(0);

    private AtomicBoolean fastForward = new AtomicBoolean(true);

    private AtomicBoolean changed = new AtomicBoolean(false);

    private StringBuilder mergeMsg = new StringBuilder();

    // In case there are conflicts
    private StringBuilder conflictMsg = new StringBuilder();

    private final Context context;

    private final StagingArea index;

    private final WorkingTree workingTree;

    private final boolean ours;

    private final ProgressListener progress;

    public MergeStatusBuilder(Context context, boolean ours, List<ObjectId> commits,
            ProgressListener progress) {
        this.context = context;
        this.index = context.index();
        this.workingTree = context.workingTree();
        this.ours = ours;
        this.progress = progress;

        ObjectId commitId = commits.get(0);
        Optional<Ref> ref = context.command(ResolveBranchId.class).setObjectId(commitId).call();
        if (ref.isPresent()) {
            mergeMsg.append("Merge branch " + ref.get().getName());
        } else {
            mergeMsg.append("Merge commit '" + commitId.toString() + "'. ");
        }
        mergeMsg.append("\n\nConflicts:\n");
    }

    public String getMergeMessage() {
        return mergeMsg.toString();
    }

    public String getConflictsMessage() {
        return conflictMsg.toString();
    }

    public void setChanged(boolean changed) {
        this.changed.set(changed);
    }

    public boolean isChanged() {
        return this.changed.get();
    }

    public void setFastFoward(boolean ff) {
        this.fastForward.set(ff);
    }

    public boolean isFastForward() {
        return this.fastForward.get();
    }

    @Override
    public void conflicted(Conflict conflict) {
        if (!ours) {
            conflictsBuffer.add(conflict);
            if (conflictsBuffer.size() == BUFFER_SIZE) {
                // Write the conflicts
                context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
                conflictsBuffer.clear();
            }
        }
        if (reportedConflicts.get() < maxReportedConflicts) {
            mergeMsg.append("\t" + conflict.getPath() + "\n");
            conflictMsg.append("CONFLICT: Merge conflict in " + conflict.getPath() + "\n");
            reportedConflicts.incrementAndGet();
        }
    }

    @Override
    public void unconflicted(DiffEntry diff) {
        diffEntryBuffer.add(diff);
        changed.set(true);
        fastForward.set(false);
    }

    @Override
    public void merged(FeatureInfo featureInfo) {
        workingTree.insert(NodeRef.parentPath(featureInfo.getPath()), featureInfo.getFeature());
        Iterator<DiffEntry> unstaged = workingTree.getUnstaged(null);
        index.stage(progress, unstaged, 0);
        changed.set(true);
        fastForward.set(false);
    }

    @Override
    public void finished() {
        try {
            if (conflictsBuffer.size() > 0) {
                // Write the conflicts
                context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
                conflictsBuffer.clear();
            }
            if (diffEntryBuffer.size() > 0) {
                progress.setDescription(String.format("Staging %,d unconflicted differences...",
                        diffEntryBuffer.size()));
                // Stage it
                index.stage(progress, diffEntryBuffer.iterator(), diffEntryBuffer.size());
            }
        } finally {
            diffEntryBuffer.close();
        }

        if (reportedConflicts.get() > maxReportedConflicts) {
            mergeMsg.append("and " + (reportedConflicts.get() - maxReportedConflicts)
                    + " additional conflicts.\n");
            conflictMsg
                    .append("and " + (reportedConflicts.get() - maxReportedConflicts) + " more.\n");
        }
        conflictMsg.append("Automatic merge failed. Fix conflicts and then commit the result.\n");
    }

    static class DiffEntrySerializer implements PersistedIterable.Serializer<DiffEntry> {

        private final PersistedIterable.Serializer<String> STRING = new PersistedIterable.StringSerializer();

        // header value for a null NodeRef
        private static final byte NULL_NODEREF_MASK = 0x00;

        // header value for a NodeRef with no default metadataId
        private static final byte PRESENT_NO_DEFAULT_METADATA = 0x01;

        // header value for a NodeRef with default metadataId
        private static final byte PRESENT_WITH_DEFAULT_METADATA = 0x02;

        private InternalByteArrayOutputStream tmpOut = new InternalByteArrayOutputStream(1024);

        private Lock writeBufferLock = new ReentrantLock();

        @Override
        public int write(DataOutputStream out, DiffEntry e) throws IOException {
            @Nullable
            NodeRef left = e.getOldObject();
            @Nullable
            NodeRef right = e.getNewObject();
            int size = 0;
            size += write(out, left);
            size += write(out, right);
            return size;
        }

        @Override
        public DiffEntry read(DataInputStream in) throws IOException {
            NodeRef left = readRef(in);
            NodeRef right = readRef(in);
            DiffEntry e = new DiffEntry(left, right);
            return e;
        }

        /**
         * If {@code ref == null}, encodes as the single byte {@link #NULL_NODEREF_MASK}, otherwise:
         *
         * <pre>
         * <code>
         * 1 byte header: PRESENT_NO_DEFAULT_METADATA | PRESENT_WITH_DEFAULT_METADATA
         * if header == PRESENT_WITH_DEFAULT_METADATA then
         *  20 byte default metadata object id
         * end if
         * N bytes noderef parent path using  PersistedIterable.StringSerializer
         * N bytes Node using FormatCommonV2.writeNode
         * </code>
         * </pre>
         * 
         * @param out
         * @param ref
         * @return
         * @throws IOException
         */
        private int write(DataOutputStream out, @Nullable NodeRef ref) throws IOException {
            int length = 1;// header
            if (ref == null) {
                out.writeByte(NULL_NODEREF_MASK);
            } else {
                ObjectId defaultMetadataId = ref.getDefaultMetadataId();
                if (defaultMetadataId.isNull()) {
                    out.writeByte(PRESENT_NO_DEFAULT_METADATA);
                } else {
                    out.writeByte(PRESENT_WITH_DEFAULT_METADATA);
                    out.write(defaultMetadataId.getRawValue());
                    length += ObjectId.NUM_BYTES;
                }
                @Nullable
                String parentPath = ref.getParentPath();
                length += STRING.write(out, parentPath);
                length += writeNode(out, ref.getNode());
            }
            return length;
        }

        @Nullable
        private NodeRef readRef(DataInputStream in) throws IOException {
            final byte mask = in.readByte();
            ObjectId defaultMetadataId = ObjectId.NULL;
            switch (mask) {
            case NULL_NODEREF_MASK:
                return null;
            case PRESENT_WITH_DEFAULT_METADATA:
                byte[] buff = new byte[ObjectId.NUM_BYTES];
                in.readFully(buff);
                defaultMetadataId = ObjectId.createNoClone(buff);
                break;
            case PRESENT_NO_DEFAULT_METADATA:
                break;
            default:
                throw new IllegalStateException("Unknown NodeRef mask header: " + mask);
            }

            String parentPath = STRING.read(in);
            Node node = readNode(in);

            NodeRef ref = new NodeRef(node, parentPath, defaultMetadataId);
            return ref;
        }

        private int writeNode(DataOutputStream out, Node node) throws IOException {
            writeBufferLock.lock();
            try {
                InternalByteArrayOutputStream tmp = this.tmpOut;
                tmp.reset();
                DataOutputStream tmpdataout = new DataOutputStream(tmp);
                FormatCommonV2.writeNode(node, tmpdataout);
                final int len = tmp.size();
                final byte[] buff = tmp.bytes();
                out.write(buff, 0, len);
                return len;
            } finally {
                writeBufferLock.unlock();
            }
        }

        private Node readNode(DataInputStream in) throws IOException {
            return FormatCommonV2.readNode(in);
        }

        private static final class InternalByteArrayOutputStream extends ByteArrayOutputStream {

            public InternalByteArrayOutputStream(int initialBuffSize) {
                super(initialBuffSize);
            }

            public byte[] bytes() {
                return super.buf;
            }

            public int size() {
                return super.count;
            }
        }
    }
}
