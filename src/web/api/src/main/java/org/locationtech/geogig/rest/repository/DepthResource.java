/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class DepthResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(new DepthRepresentation(request));
    }

    private static class DepthRepresentation extends OutputRepresentation {

        private Request request;

        public DepthRepresentation(Request request) {
            super(MediaType.TEXT_PLAIN);
            this.request = request;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            PrintWriter w = new PrintWriter(out);
            Form options = request.getResourceRef().getQueryAsForm();

            Optional<String> commit = Optional
                    .fromNullable(options.getFirstValue("commitId", null));

            Optional<GeoGIG> geogig = getGeogig(request);
            Preconditions.checkState(geogig.isPresent());
            GeoGIG ggit = geogig.get();

            Optional<Integer> depth = Optional.absent();

            if (commit.isPresent()) {
                depth = Optional.of(ggit.getRepository().graphDatabase()
                        .getDepth(ObjectId.valueOf(commit.get())));
            } else {
                depth = ggit.getRepository().getDepth();
            }

            if (depth.isPresent()) {
                w.write(depth.get().toString());
            }
            w.flush();

        }

    }
}
