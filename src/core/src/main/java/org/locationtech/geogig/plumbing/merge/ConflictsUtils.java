/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - extracted out as utility class from MergeStatusBuilder
 */
package org.locationtech.geogig.plumbing.merge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_2;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.locationtech.geogig.storage.impl.PersistedIterable.Serializer;

import com.google.common.annotations.VisibleForTesting;

public class ConflictsUtils {

    /**
     * Makes for a {@link #conflictsBuffer} of ~14MB
     */
    private final static int BUFFER_SIZE = 100_000;

    public static PersistedIterable<Conflict> newTemporaryConflictStream() {
        final boolean compress = true;
        final Path tmpDir = null;
        PersistedIterable<Conflict> conflictsBuffer;
        conflictsBuffer = new PersistedIterable<>(tmpDir, CONFLICT_SERIALIZER, BUFFER_SIZE,
                compress);
        return conflictsBuffer;
    }

    public static PersistedIterable<FeatureInfo> newTemporaryFeatureInfoStream() {
        return new PersistedIterable<>(null, new ConflictsUtils.FeatureInfoSerializer(), 10_000,
                true);
    }

    public static PersistedIterable<DiffEntry> newTemporaryDiffEntryStream() {
        return new PersistedIterable<>(null, new ConflictsUtils.DiffEntrySerializer(), BUFFER_SIZE,
                true);
    }

    static @VisibleForTesting class DiffEntrySerializer implements PersistedIterable.Serializer<DiffEntry> {

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
                    MergeStatusBuilder.OID.write(out, defaultMetadataId);
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
                defaultMetadataId = MergeStatusBuilder.OID.read(in);
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

    private static class FeatureInfoSerializer implements Serializer<FeatureInfo> {

        @Override
        public void write(DataOutputStream out, FeatureInfo value) throws IOException {
            String path = value.getPath();
            ObjectId featureTypeId = value.getFeatureTypeId();
            RevFeature feature = value.getFeature();
            out.writeUTF(path);
            MergeStatusBuilder.OID.write(out, featureTypeId);
            DataStreamRevObjectSerializerV2.INSTANCE.write(feature, out);
        }

        @Override
        public FeatureInfo read(DataInputStream in) throws IOException {
            String path = in.readUTF();
            ObjectId featureTypeId = MergeStatusBuilder.OID.read(in);
            RevFeature feature = (RevFeature) DataStreamRevObjectSerializerV2.INSTANCE.read(in);
            return FeatureInfo.insert(feature, featureTypeId, path);
        }

    }

    private static final PersistedIterable.Serializer<Conflict> CONFLICT_SERIALIZER = new PersistedIterable.Serializer<Conflict>() {

        private static final byte HAS_ANCESTOR = 0b00000001;

        private static final byte HAS_OURS = 0b00000010;

        private static final byte HAS_THEIRS = 0b00000100;

        public @Override void write(DataOutputStream out, Conflict value) throws IOException {

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
                MergeStatusBuilder.OID.write(out, ancestor);
            }
            if (!ours.isNull()) {
                MergeStatusBuilder.OID.write(out, ours);
            }
            if (!theirs.isNull()) {
                MergeStatusBuilder.OID.write(out, theirs);
            }
        }

        public @Override Conflict read(DataInputStream in) throws IOException {
            byte flags = in.readByte();
            boolean hasAncestor = (flags & HAS_ANCESTOR) == HAS_ANCESTOR;
            boolean hasOurs = (flags & HAS_OURS) == HAS_OURS;
            boolean hasTheirs = (flags & HAS_THEIRS) == HAS_THEIRS;
            String path = in.readUTF();
            ObjectId ancestor = hasAncestor ? MergeStatusBuilder.OID.read(in) : ObjectId.NULL;
            ObjectId ours = hasOurs ? MergeStatusBuilder.OID.read(in) : ObjectId.NULL;
            ObjectId theirs = hasTheirs ? MergeStatusBuilder.OID.read(in) : ObjectId.NULL;
            return new Conflict(path, ancestor, ours, theirs);
        }
    };

}
