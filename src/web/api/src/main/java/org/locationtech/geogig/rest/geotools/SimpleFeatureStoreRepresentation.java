/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;

import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncCommandBinaryRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandBinaryRepresentationFactory;
import org.restlet.data.MediaType;

import com.google.common.io.ByteStreams;

/**
 * Representation for asynchronous commands with SimpleFeatureStore results AND a binary file that
 * contains the result that can be streamed back to the consumer.
 */
public class SimpleFeatureStoreRepresentation extends
        AsyncCommandBinaryRepresentation<SimpleFeatureStore> {

    protected final File binary;

    public SimpleFeatureStoreRepresentation(MediaType mediaType,
            AsyncCommand<SimpleFeatureStore> cmd, File binary) {
        super(binary, mediaType, cmd);
        // keep local reference to the file
        this.binary = binary;
    }

    @Override
    public void write(OutputStream outputStream) {
        try {
            // Block until get() returns. We don't actually do anything with the result, we just
            // need to wait for the operation to complete. Once complete, we can stream the binary
            // file back.
            // NOTE: This will not work if the command takes longer than the TCP timeout to
            // complete. The client has issued a GET for this data, so the clock is ticking. If the
            // command blocks too long, we won't start sending data back to the client and the
            // connection will close due to data timeout. Eventually, this should be changed to
            // return a data retrieval URL that the client can poll and retrieve when complete.
            // Until then, this solution is somewhat fragile.
            command.get();
            // command is finished, send the binary file contents out through the output stream
            try (FileInputStream stream = getStream()) {
                ByteStreams.copy(stream, outputStream);
            }
            // flush the stream
            outputStream.flush();
        } catch (IOException | InterruptedException | ExecutionException ex) {
            throw new RuntimeException("Could not successfully stream File", ex);
        } finally {
            // remove the file, we don't want it hanging around
            binary.delete();
        }
    }

    public static class Factory implements CommandBinaryRepresentationFactory<SimpleFeatureStore> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return ExportOp.class.isAssignableFrom(cmdClass);
        }

        @Override
        public AsyncCommandBinaryRepresentation<SimpleFeatureStore> newRepresentation(
                AsyncCommand<SimpleFeatureStore> cmd, MediaType mediaType, File binary) {
            return new SimpleFeatureStoreRepresentation(mediaType, cmd, binary);
        }

    }
}
