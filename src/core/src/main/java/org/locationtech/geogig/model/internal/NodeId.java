/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;

class NodeId {

    protected final String name;

    @Nullable
    private final Object value;

    protected NodeId(final String name) {
        this.name = name;
        this.value = null;
    }

    protected NodeId(final String name, final Object value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    @Nullable
    public <V> V value() {
        return (V) value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NodeId)) {
            return false;
        }
        NodeId n = (NodeId) o;
        boolean equals = Objects.equals(name, n.name) && Objects.equals(value, n.value);
        return equals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return String.format("NodeId[%s, %s]", name, value);
    }

    public static void write(NodeId id, DataOutput out) throws IOException {
        checkNotNull(id);
        checkNotNull(out);

        final String name = id.name();
        @Nullable
        final Object value = id.value();

        final FieldType valueType = FieldType.forValue((Object) id.value());

        out.writeUTF(name);
        out.writeByte(valueType.ordinal());
        DataStreamValueSerializerV2.write(valueType, value, out);
    }

    public static NodeId read(DataInput in) throws IOException {
        final String name = in.readUTF();
        FieldType type = FieldType.valueOf(in.readUnsignedByte());
        final Object val = DataStreamValueSerializerV2.read(type, in);
        return new NodeId(name, val);
    }
}