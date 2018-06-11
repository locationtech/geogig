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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.impl.PersistedIterable;

public class ConflictsUtils {

    private final static int BUFFER_SIZE = 100_000;

    public static PersistedIterable<Conflict> newTemporaryConflictStream() {
        final boolean compress = true;
        final Path tmpDir = null;
        PersistedIterable<Conflict> conflictsBuffer;
        conflictsBuffer = new PersistedIterable<>(tmpDir, CONFLICT_SERIALIZER, BUFFER_SIZE,
                compress);
        return conflictsBuffer;
    }

    public static final PersistedIterable.Serializer<Conflict> CONFLICT_SERIALIZER = new PersistedIterable.Serializer<Conflict>() {

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
