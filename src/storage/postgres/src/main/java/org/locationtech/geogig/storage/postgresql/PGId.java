/* Copyright (c) 2015-2016 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import org.locationtech.geogig.api.ObjectId;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/**
 * Converts {@link ObjectId}s to and from its stored representation.
 * <p>
 * </p>
 */
final class PGId {

    private final byte[] id;

    public PGId(byte[] oid) {
        this.id = oid;
    }

    public static int intHash(ObjectId id) {
        final int hash1 = ((id.byteN(0) << 24) //
                | (id.byteN(1) << 16) //
                | (id.byteN(2) << 8) //
        | (id.byteN(3)));
        return hash1;
    }

    public static int intHash(byte[] id) {
        final int hash1 = ((((int) id[0]) << 24) //
                | (((int) id[1] & 0xFF) << 16) //
                | (((int) id[2] & 0xFF) << 8) //
        | (((int) id[3] & 0xFF)));
        return hash1;
    }

    public int hash1() {
        return PGId.intHash(this.id);
    }

    public long hash2() {
        final long hash2 = ((((long) id[4]) << 56) //
                | (((long) id[5] & 0xFF) << 48)//
                | (((long) id[6] & 0xFF) << 40) //
                | (((long) id[7] & 0xFF) << 32) //
                | (((long) id[8] & 0xFF) << 24) //
                | (((long) id[9] & 0xFF) << 16) //
                | (((long) id[10] & 0xFF) << 8)//
        | (((long) id[11] & 0xFF)));
        return hash2;
    }

    public long hash3() {
        final long hash3 = ((((long) id[12]) << 56) //
                | (((long) id[13] & 0xFF) << 48)//
                | (((long) id[14] & 0xFF) << 40) //
                | (((long) id[15] & 0xFF) << 32) //
                | (((long) id[16] & 0xFF) << 24) //
                | (((long) id[17] & 0xFF) << 16) //
                | (((long) id[18] & 0xFF) << 8)//
        | (((long) id[19] & 0xFF)));
        return hash3;
    }

    public ObjectId toObjectId() {
        return ObjectId.createNoClone(id);
    }

    public static PGId valueOf(ObjectId oid) {
        return valueOf(oid.getRawValue());
    }

    public static PGId valueOf(byte[] oid) {
        return new PGId(oid);
    }

    public static PGId valueOf(final int h1, final long h2, final long h3) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeInt(h1);
        out.writeLong(h2);
        out.writeLong(h3);
        byte[] raw = out.toByteArray();
        return new PGId(raw);
    }

    @Override
    public String toString() {
        return String.format("ID[%d, %d, %d]", hash1(), hash2(), hash3());
    }
}