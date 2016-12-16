/* Copyright (c) 2016 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

/**
 * An encoder for {@link RevObject} instances that delegates the the best available
 * {@link ObjectSerializingFactory} while maintaining backwards compatibility.
 * <p>
 * The purpose of this encoder is to allow improved {@link ObjectSerializingFactory} implementations
 * to be added transparently, while maintaining backwards compatibility with objects encoded with
 * prior versions of the serialization format.
 * <p>
 * Serialized representations of {@code RevObject}s created by this encoder are composed of a
 * one-byte header followed by the encoded result of the concrete {@link ObjectSerializingFactory}
 * this encoder delegates to.
 * <p>
 * When deserializing, the header is used to identify which serializer "version" the object was
 * encoded with, and delegate to the proper serializer. This way, there can be objects of mixed
 * serialization formats in the same database transparently, so upgrading the serialization format
 * requires no extra maintenance.
 */
public class SerializationFactoryProxy implements ObjectSerializingFactory {
    /**
     * The serialized object is added a header that's one unsigned byte with the index of the
     * corresponding factory in this array
     */
    private static final ObjectSerializingFactory[] SUPPORTED_FORMATS = { //
            new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE) //
    };

    private static final int MAX_FORMAT_CODE = SUPPORTED_FORMATS.length - 1;

    /**
     * The serialization factory used for writing is the highest supported version one
     */
    private static final ObjectSerializingFactory WRITER = SUPPORTED_FORMATS[MAX_FORMAT_CODE];

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
        final ObjectSerializingFactory serializer = SUPPORTED_FORMATS[serialVersionHeader];
        RevObject revObject = serializer.read(id, in);
        return revObject;
    }

    /**
     * Reads object from its binary representation as stored in the database.
     */
    public RevObject decode(final ObjectId id, final byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return read(id, in);
        } catch (IOException e) {
            throw new RuntimeException("Error reading object " + id, e);
        }
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

}
