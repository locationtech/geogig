/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;

/**
 * An encoder for {@link RevObject} instances that delegates the the best available
 * {@link RevObjectSerializer} while maintaining backwards compatibility.
 * <p>
 * The purpose of this encoder is to allow improved {@link RevObjectSerializer} implementations
 * to be added transparently, while maintaining backwards compatibility with objects encoded with
 * prior versions of the serialization format.
 * <p>
 * Serialized representations of {@code RevObject}s created by this encoder are composed of a
 * one-byte header followed by the encoded result of the concrete {@link RevObjectSerializer}
 * this encoder delegates to.
 * <p>
 * When deserializing, the header is used to identify which serializer "version" the object was
 * encoded with, and delegate to the proper serializer. This way, there can be objects of mixed
 * serialization formats in the same database transparently, so upgrading the serialization format
 * requires no extra maintenance.
 */
public class SerializationFactoryProxy implements RevObjectSerializer {
    /**
     * The serialized object is added a header that's one unsigned byte with the index of the
     * corresponding factory in this array
     */
    private static final RevObjectSerializer[] SUPPORTED_FORMATS = { //
            new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2_2.INSTANCE)//
    };

    private static final int MAX_FORMAT_CODE = SUPPORTED_FORMATS.length - 1;

    /**
     * The serialization factory used for writing is the highest supported version one
     */
    private static final RevObjectSerializer WRITER = SUPPORTED_FORMATS[MAX_FORMAT_CODE];

    @Override
    public void write(RevObject o, OutputStream out) throws IOException {
        final int storageVersionHeader = MAX_FORMAT_CODE;
        out.write(storageVersionHeader);
        WRITER.write(o, out);
    }

    @Override
    public RevObject read(ObjectId id, InputStream in) throws IOException {
        final int serialVersionHeader = in.read();
        assert serialVersionHeader >= 0 && serialVersionHeader <= MAX_FORMAT_CODE;
        final RevObjectSerializer serializer = serializer(id, serialVersionHeader);
        RevObject revObject = serializer.read(id, in);
        return revObject;
    }

    @Override
    public RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length) {
        final int serialVersionHeader = data[offset] & 0xFF;
        assert serialVersionHeader >= 0 && serialVersionHeader <= MAX_FORMAT_CODE;
        final RevObjectSerializer serializer = serializer(id, serialVersionHeader);
        RevObject revObject;
        try {
            revObject = serializer.read(id, data, offset + 1, length - 1);
        } catch (IOException e) {
            throw new RuntimeException("Error reading object " + id, e);
        }
        return revObject;
    }

    private RevObjectSerializer serializer(final @Nullable ObjectId id,
            final int serializerIndex) {
        if (serializerIndex < 0) {
            throw new RuntimeException(
                    String.format("Serializer header shall be between 0 and %d, got %d",
                            MAX_FORMAT_CODE, serializerIndex));
        }
        if (serializerIndex > MAX_FORMAT_CODE) {
            throw new RuntimeException(String.format(
                    "Object %s was created with serial format %d, which is unsupported by "
                            + "this geogig version (max format supported: %d)", //
                    (id == null ? "" : id.toString()), //
                    serializerIndex, //
                    MAX_FORMAT_CODE));
        }
        return SUPPORTED_FORMATS[serializerIndex];
    }

    /**
     * Reads object from its binary representation as stored in the database.
     */
    public RevObject decode(final ObjectId id, final byte[] bytes) {
        return read(id, bytes, 0, bytes.length);
    }

    public byte[] encode(final RevObject o) {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            write(o, bout);
            byte[] bytes = bout.toByteArray();
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException("Error encoding object " + o, e);
        }
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("Proxy[");
        for (RevObjectSerializer f : SUPPORTED_FORMATS) {
            sb.append(f.getDisplayName()).append(", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        sb.append(']');
        return sb.toString();
    }

}
