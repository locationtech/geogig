/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.format.lzf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

import lombok.NonNull;

/**
 * Wrapper Factory that deflates/inflates data written to/read from streams using LZF compression.
 */
public class RevObjectSerializerLZF implements RevObjectSerializer {

    private final RevObjectSerializer factory;

    /**
     * ChunkDecoder is stateless and can be reused concurrently, so cache it to avoid the factory
     * lookup on each call
     */
    private static final ChunkDecoder CHUNK_DECODER = ChunkDecoderFactory.optimalInstance();

    public RevObjectSerializerLZF(final @NonNull RevObjectSerializer factory) {
        this.factory = factory;
    }

    public @Override RevObject read(ObjectId id, InputStream rawData) throws IOException {
        // decompress the stream
        LZFInputStream inflatedInputeStream = new LZFInputStream(CHUNK_DECODER, rawData);
        return factory.read(id, inflatedInputeStream);

    }

    public @Override RevObject read(ObjectId id, @NonNull byte[] data, int offset, int length)
            throws IOException {
        byte[] decoded = LZFDecoder.decode(data, offset, length);
        return factory.read(id, decoded, 0, decoded.length);
    }

    public @Override void write(RevObject o, OutputStream out) throws IOException {
        // compress the stream
        LZFOutputStream deflatedOutputStream = new LZFOutputStream(out);
        deflatedOutputStream.setFinishBlockOnFlush(true);
        factory.write(o, deflatedOutputStream);
        deflatedOutputStream.flush();
    }

    public @Override String getDisplayName() {
        return factory.getDisplayName() + "/LZF";
    }
}
