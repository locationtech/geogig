/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.io.InputStream;

import org.locationtech.geogig.remote.http.BinaryPackedObjects;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.SendObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;
import com.google.common.io.CountingInputStream;

/**
 *
 */
@Service("legacySendObjectService")
public class LegacySendObjectService extends AbstractRepositoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacySendObjectService.class);

    public SendObject sendObject(RepositoryProvider provider, String repoName,
            InputStream request) {
        final SendObject sendObject = new SendObject();
        final Repository repository = getRepository(provider, repoName);
        final BinaryPackedObjects unpacker = new BinaryPackedObjects(repository.objectDatabase());

        CountingInputStream countingStream = new CountingInputStream(request);

        Stopwatch sw = Stopwatch.createStarted();
        BinaryPackedObjects.IngestResults ingestResults = unpacker.ingest(countingStream);
        sw.stop();
        sendObject.setExisting(ingestResults.getExisting())
                .setInserted(ingestResults.getInserted());
        LOGGER.info(String.format(
                "SendObjectResource: Processed %,d objects.\nInserted: %,d.\nExisting: %,d.\nTime to process: %s.\nStream size: %,d bytes.\n",
                ingestResults.total(), ingestResults.getInserted(), ingestResults.getExisting(),
                sw, countingStream.getCount()));
        return sendObject;
    }
}
