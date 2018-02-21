/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - factored out from MergeOp
 */
package org.locationtech.geogig.plumbing.merge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.ResolveBranchId;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_2;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.locationtech.geogig.storage.impl.PersistedIterable.Serializer;

import com.google.common.base.Optional;

public class MergeStatusBuilder extends MergeScenarioConsumer {

    /**
     * Makes for a {@link #conflictsBuffer} of ~14MB
     */
    private final static int BUFFER_SIZE = 100_000;

    final PersistedIterable<FeatureInfo> mergedBuffer = new PersistedIterable<>(null,
            new FeatureInfoSerializer(), 10_000, true);

    final PersistedIterable<Conflict> conflictsBuffer = new PersistedIterable<>(null,
            new ConflictSerializer(), BUFFER_SIZE, true);

    final PersistedIterable<DiffEntry> unconflictedBuffer = new PersistedIterable<>(null,
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
        this.index = context.stagingArea();
        this.workingTree = context.workingTree();
        this.ours = ours;
        this.progress = progress;
        progress.setMaxProgress(0);

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
        }
        if (reportedConflicts.get() < maxReportedConflicts) {
            mergeMsg.append("\t" + conflict.getPath() + "\n");
            conflictMsg.append("CONFLICT: Merge conflict in " + conflict.getPath() + "\n");
            reportedConflicts.incrementAndGet();
        }
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    public void unconflicted(DiffEntry diff) {
        unconflictedBuffer.add(diff);
        changed.set(true);
        fastForward.set(false);
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    public void merged(FeatureInfo featureInfo) {
        mergedBuffer.add(featureInfo);
        changed.set(true);
        fastForward.set(false);
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    protected void cancelled() {
        dispose();
    }

    @Override
    public void finished() {
        progress.complete();
        progress.started();
        try {
            AutoCloseableIterator<DiffEntry> unstaged = AutoCloseableIterator.emptyIterator();
            if (mergedBuffer.size() > 0) {
                progress.setDescription(
                        String.format("Saving %,d merged features...", mergedBuffer.size()));
                workingTree.insert(mergedBuffer.iterator(), progress);
                unstaged = workingTree.getUnstaged(null);
            }
            if (unconflictedBuffer.size() > 0 || unstaged.hasNext()) {
                progress.setDescription(
                        String.format("Staging %,d unconflicted and %,d merged differences...",
                                unconflictedBuffer.size(), mergedBuffer.size()));

                long size = unconflictedBuffer.size() + mergedBuffer.size();
                unstaged = AutoCloseableIterator.concat(unstaged,
                        AutoCloseableIterator.fromIterator(unconflictedBuffer.iterator()));
                // Stage it
                index.stage(progress, unstaged, size);
            }
            if (conflictsBuffer.size() > 0) {
                // Write the conflicts
                progress.setDescription(
                        String.format("Saving %,d conflicts...", conflictsBuffer.size()));
                context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
            }
        } finally {
            dispose();
        }

        if (reportedConflicts.get() > maxReportedConflicts) {
            mergeMsg.append("and " + (reportedConflicts.get() - maxReportedConflicts)
                    + " additional conflicts.\n");
            conflictMsg
                    .append("and " + (reportedConflicts.get() - maxReportedConflicts) + " more.\n");
        }
        conflictMsg.append("Automatic merge failed. Fix conflicts and then commit the result.\n");
    }

    private void dispose() {
        try {
            conflictsBuffer.close();
        } finally {
            try {
                unconflictedBuffer.close();
            } finally {
                mergedBuffer.close();
            }
        }
    }

    private static Serializer<ObjectId> OID = new Serializer<ObjectId>() {

        @Override
        public void write(DataOutputStream out, ObjectId value) throws IOException {
            value.writeTo(out);
        }

        @Override
        public ObjectId read(DataInputStream in) throws IOException {
            return ObjectId.readFrom(in);
        }
    };

    static class DiffEntrySerializer implements PersistedIterable.Serializer<DiffEntry> {

        private final PersistedIterable.Serializer<String> STRING = new PersistedIterable.StringSerializer();

        // header value for a null NodeRef
        private static final byte NULL_NODEREF_MASK = 0x00;

        // header value for a NodeRef with no default metadataId
        private static final byte PRESENT_NO_DEFAULT_METADATA = 0x01;

        // header value for a NodeRef with default metadataId
        private static final byte PRESENT_WITH_DEFAULT_METADATA = 0x02;

        @Override
        public void write(DataOutputStream out, DiffEntry e) throws IOException {
            @Nullable
            NodeRef left = e.getOldObject();
            @Nullable
            NodeRef right = e.getNewObject();
            write(out, left);
            write(out, right);
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
        private void write(DataOutputStream out, @Nullable NodeRef ref) throws IOException {
            if (ref == null) {
                out.writeByte(NULL_NODEREF_MASK);
            } else {
                ObjectId defaultMetadataId = ref.getDefaultMetadataId();
                if (defaultMetadataId.isNull()) {
                    out.writeByte(PRESENT_NO_DEFAULT_METADATA);
                } else {
                    out.writeByte(PRESENT_WITH_DEFAULT_METADATA);
                    OID.write(out, defaultMetadataId);
                }
                @Nullable
                String parentPath = ref.getParentPath();
                STRING.write(out, parentPath);
                writeNode(out, ref.getNode());
            }
        }

        @Nullable
        private NodeRef readRef(DataInputStream in) throws IOException {
            final byte mask = in.readByte();
            ObjectId defaultMetadataId = ObjectId.NULL;
            switch (mask) {
            case NULL_NODEREF_MASK:
                return null;
            case PRESENT_WITH_DEFAULT_METADATA:
                defaultMetadataId = OID.read(in);
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

        private void writeNode(DataOutputStream out, Node node) throws IOException {
            FormatCommonV2_2.INSTANCE.writeNode(node, out);
        }

        private Node readNode(DataInputStream in) throws IOException {
            return FormatCommonV2_2.INSTANCE.readNode(in);
        }

    }

    static class ConflictSerializer implements PersistedIterable.Serializer<Conflict> {

        private static final byte HAS_ANCESTOR = 0b00000001;

        private static final byte HAS_OURS = 0b00000010;

        private static final byte HAS_THEIRS = 0b00000100;

        @Override
        public void write(DataOutputStream out, Conflict value) throws IOException {

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
                OID.write(out, ancestor);
            }
            if (!ours.isNull()) {
                OID.write(out, ours);
            }
            if (!theirs.isNull()) {
                OID.write(out, theirs);
            }
        }

        @Override
        public Conflict read(DataInputStream in) throws IOException {
            byte flags = in.readByte();
            boolean hasAncestor = (flags & HAS_ANCESTOR) == HAS_ANCESTOR;
            boolean hasOurs = (flags & HAS_OURS) == HAS_OURS;
            boolean hasTheirs = (flags & HAS_THEIRS) == HAS_THEIRS;
            String path = in.readUTF();
            ObjectId ancestor = hasAncestor ? OID.read(in) : ObjectId.NULL;
            ObjectId ours = hasOurs ? OID.read(in) : ObjectId.NULL;
            ObjectId theirs = hasTheirs ? OID.read(in) : ObjectId.NULL;
            return new Conflict(path, ancestor, ours, theirs);
        }
    }

    private static class FeatureInfoSerializer implements Serializer<FeatureInfo> {

        @Override
        public void write(DataOutputStream out, FeatureInfo value) throws IOException {
            String path = value.getPath();
            ObjectId featureTypeId = value.getFeatureTypeId();
            RevFeature feature = value.getFeature();
            out.writeUTF(path);
            OID.write(out, featureTypeId);
            DataStreamSerializationFactoryV2.INSTANCE.write(feature, out);
        }

        @Override
        public FeatureInfo read(DataInputStream in) throws IOException {
            String path = in.readUTF();
            ObjectId featureTypeId = OID.read(in);
            RevFeature feature = (RevFeature) DataStreamSerializationFactoryV2.INSTANCE.read(in);
            return FeatureInfo.insert(feature, featureTypeId, path);
        }

    }
}
