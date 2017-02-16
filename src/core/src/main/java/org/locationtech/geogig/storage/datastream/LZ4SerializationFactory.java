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
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

import com.google.common.base.Preconditions;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Wrapper Factory that deflates/inflates data written to/read from streams using LZ4 compression.
 */
public class LZ4SerializationFactory implements ObjectSerializingFactory {

    // cache factory to avoid thread contention when looking up for the fastest instance on some LZ4
    // lib synchronized blocks
    private static final LZ4Factory lz4factory = LZ4Factory.fastestInstance();

    // cache factory to avoid thread contention when looking up for the fastest instance on some LZ4
    // lib synchronized blocks
    private static final XXHashFactory hashFactory = XXHashFactory.fastestInstance();

    private static final int DEFAULT_SEED = 0x9747b28c;

    private final ObjectSerializingFactory factory;

    public LZ4SerializationFactory(final ObjectSerializingFactory factory) {
        Preconditions.checkNotNull(factory);
        this.factory = factory;
    }

    @Override
    public RevObject read(ObjectId id, InputStream in) throws IOException {
        LZ4FastDecompressor decompressor = lz4factory.fastDecompressor();
        Checksum checksum = newChecksum();
        try (LZ4BlockInputStream cin = new LZ4BlockInputStream(in, decompressor, checksum)) {
            return factory.read(id, cin);
        }
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
        try (LZ4BlockOutputStream cout = new LZ4BlockOutputStream(out, blockSize, compressor,
                checksum, false)) {
            factory.write(o, cout);
        }
    }

    @Override
    public String getDisplayName() {
        return factory.getDisplayName() + "/LZ4";
    }
}
