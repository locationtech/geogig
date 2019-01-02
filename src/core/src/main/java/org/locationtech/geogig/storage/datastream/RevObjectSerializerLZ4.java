/* Copyright (c) 2017 Boundless and others.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Checksum;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Wrapper Factory that deflates/inflates data written to/read from streams using LZ4 compression.
 * <p>
 * Note this serialization factory is experimental and does not support reading multiple objects
 * from a single stream where each object has been compressed individually. See
 * <a href="https://github.com/lz4/lz4-java/issues/48">lz-java issue #48</a>.
 */
@Beta
public class RevObjectSerializerLZ4 implements RevObjectSerializer {

    // cache factory to avoid thread contention when looking up for the fastest instance on some LZ4
    // lib synchronized blocks
    private static final LZ4Factory lz4factory = LZ4Factory.fastestInstance();

    // cache factory to avoid thread contention when looking up for the fastest instance on some LZ4
    // lib synchronized blocks
    private static final XXHashFactory hashFactory = XXHashFactory.fastestInstance();

    private static final int DEFAULT_SEED = 0x9747b28c;

    private final RevObjectSerializer factory;

    public RevObjectSerializerLZ4(final RevObjectSerializer factory) {
        Preconditions.checkNotNull(factory);
        this.factory = factory;
    }

    /**
     * @return {@code false}, this serializer does not support reading back multiple objects from a
     *         single stream where each object has been individually compressed, due to a limitation
     *         in {@code lz-java}
     */
    public @Override boolean supportsStreaming() {
        return false;
    }

    @Override
    public RevObject read(ObjectId id, InputStream in) throws IOException {
        LZ4FastDecompressor decompressor = lz4factory.fastDecompressor();
        Checksum checksum = newChecksum();
        LZ4BlockInputStream cin = new LZ4BlockInputStream(in, decompressor, checksum);
        return factory.read(id, cin);

    }

    private Checksum newChecksum() {
        Checksum checksum = hashFactory.newStreamingHash32(DEFAULT_SEED).asChecksum();
        return checksum;
    }

    @Override
    public RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data, offset, length);
        return read(id, in);
    }

    @Override
    public void write(RevObject o, OutputStream out) throws IOException {
        final int blockSize = 1 << 16;
        LZ4Compressor compressor = lz4factory.fastCompressor();
        Checksum checksum = newChecksum();
        LZ4BlockOutputStream cout = new LZ4BlockOutputStream(out, blockSize, compressor, checksum,
                true);
        factory.write(o, cout);
        cout.finish();// same as close but not closing the wrapped stream
    }

    @Override
    public String getDisplayName() {
        return factory.getDisplayName() + "/LZ4";
    }
}
