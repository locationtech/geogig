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
import java.io.Writer;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.repository.Repository;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class ObjectExistsResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();

        variants.add(new ObjectExistsRepresentation());
    }

    private class ObjectExistsRepresentation extends WriterRepresentation {
        public ObjectExistsRepresentation() {
            super(MediaType.TEXT_PLAIN);
        }

        @Override
        public void write(Writer w) throws IOException {
            Form options = getRequest().getResourceRef().getQueryAsForm();

            ObjectId oid = ObjectId.valueOf(options.getFirstValue("oid", ObjectId.NULL.toString()));
            Request request = getRequest();
            Optional<GeoGIG> ggit = getGeogig(request);
            Preconditions.checkState(ggit.isPresent());

            GeoGIG geogig = ggit.get();
            Repository repository = geogig.getRepository();
            boolean blobExists = repository.blobExists(oid);

            if (blobExists) {
                w.write("1");
            } else {
                w.write("0");
            }
            w.flush();
        }
    }
}
