/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObjects;

/**
 * Converts {@link ObjectId}s to and from its stored representation.
 * <p>
 * </p>
 */
public final class PGId {

    private final int h1;

    private final long h2;

    private final long h3;

    public PGId(final int h1, final long h2, final long h3) {
        this.h1 = h1;
        this.h2 = h2;
        this.h3 = h3;
    }

    public int hash1() {
        return h1;
    }

    public long hash2() {
        return h2;
    }

    public long hash3() {
        return h3;
    }

    public ObjectId toObjectId() {
        return ObjectId.create(h1, h2, h3);
    }

    public static PGId valueOf(ObjectId oid) {
        return valueOf(RevObjects.h1(oid), RevObjects.h2(oid), RevObjects.h3(oid));
    }

    public static PGId valueOf(final int h1, final long h2, final long h3) {
        return new PGId(h1, h2, h3);
    }

    @Override
    public String toString() {
        return String.format("ID[%d, %d, %d]", hash1(), hash2(), hash3());
    }

    public void setArgs(PreparedStatement ps, final int startIndex) throws SQLException {
        ps.setInt(startIndex, hash1());
        ps.setLong(startIndex + 1, hash2());
        ps.setLong(startIndex + 2, hash3());
    }

    public static PGId valueOf(ResultSet rs, int startIndex) throws SQLException {
        return valueOf(rs.getInt(startIndex), rs.getLong(startIndex + 1),
                rs.getLong(startIndex + 2));
    }
}