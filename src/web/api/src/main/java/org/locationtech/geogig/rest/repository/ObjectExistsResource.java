/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.WriterRepresentation;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamWriterRepresentation;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

/**
 *
 */
public class ObjectExistsResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();

        // variants.add(TEXT_PLAIN);
    }

    @Override
    public void handleGet() {
        final Request request = getRequest();

        Optional<Repository> geogig = RESTUtils.getGeogig(request);
        if (!geogig.isPresent() || !geogig.get().isOpen()) {
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("Repository not found.")));
            return;
        }

        Form options = request.getResourceRef().getQueryAsForm();
        final String objectIdStr = options.getFirstValue("oid");
        if (objectIdStr == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("You must specify an object id.")));
            return;
        }

        ObjectId objectId;
        try {
            objectId = ObjectId.valueOf(objectIdStr);
        } catch (Exception e) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("You must specify a valid object id.")));
            return;
        }

        boolean blobExists = geogig.get().blobExists(objectId);

        getResponse().setEntity(new ObjectExistsRepresentation(blobExists));
    }

    private class ObjectExistsRepresentation extends WriterRepresentation {
        private final boolean blobExists;

        public ObjectExistsRepresentation(boolean blobExists) {
            super(MediaType.TEXT_PLAIN);
            this.blobExists = blobExists;
        }

        @Override
        public void write(Writer w) throws IOException {
            if (blobExists) {
                w.write("1");
            } else {
                w.write("0");
            }
            w.flush();
        }
    }
}
