/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.InputStream;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.remote.BinaryPackedObjects;
import org.locationtech.geogig.remote.BinaryPackedObjects.IngestResults;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.io.Closeables;
import com.google.common.io.CountingInputStream;

/**
 *
 */
public class SendObjectResource extends Resource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendObjectResource.class);

    @Override
    public boolean allowPost() {
        return true;
    }

    @Override
    public void post(Representation entity) {
        InputStream input = null;

        Request request = getRequest();
        try {
            LOGGER.info("Receiving objects from {}", request.getClientInfo().getAddress());
            Representation representation = request.getEntity();
            input = representation.getStream();
            final GeoGIG ggit = getGeogig(request).get();
            final BinaryPackedObjects unpacker = new BinaryPackedObjects(ggit.getRepository()
                    .objectDatabase());

            CountingInputStream countingStream = new CountingInputStream(input);

            Stopwatch sw = Stopwatch.createStarted();
            IngestResults ingestResults = unpacker.ingest(countingStream);
            sw.stop();

            LOGGER.info(String
                    .format("SendObjectResource: Processed %,d objects.\nInserted: %,d.\nExisting: %,d.\nTime to process: %s.\nStream size: %,d bytes.\n",
                            ingestResults.total(), ingestResults.getInserted(),
                            ingestResults.getExisting(), sw, countingStream.getCount()));

        } catch (IOException e) {
            LOGGER.warn("Error processing incoming objects from {}", request.getClientInfo()
                    .getAddress(), e);
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL, e);
        } finally {
            if (input != null)
                Closeables.closeQuietly(input);
        }
    }
}
