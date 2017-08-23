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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamWriterRepresentation;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 *
 */
public class ParentResource extends Resource {

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
        final String commitIdStr = options.getFirstValue("commitId");

        ObjectId commitId = null;
        if (commitIdStr != null) {
            try {
                commitId = ObjectId.valueOf(commitIdStr);
            } catch (Exception e) {
                getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                        StreamResponse.error("You must specify a valid commit id.")));
                return;
            }
        }

        getResponse().setEntity(new ParentRepresentation(commitId, geogig.get()));
    }

    private static class ParentRepresentation extends OutputRepresentation {

        private final ObjectId commitId;

        private final Repository repo;

        public ParentRepresentation(ObjectId commitId, Repository repo) {
            super(MediaType.TEXT_PLAIN);
            this.commitId = commitId;
            this.repo = repo;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            PrintWriter w = new PrintWriter(out);

            if (commitId != null) {
                ImmutableList<ObjectId> parents = repo.graphDatabase().getParents(commitId);
                for (ObjectId object : parents) {
                    w.write(object.toString() + "\n");
                }
            }
            w.flush();

        }

    }
}
