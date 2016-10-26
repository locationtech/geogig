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

import static org.locationtech.geogig.web.api.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.WriterRepresentation;
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
            Optional<Repository> ggig = getGeogig(request);
            Preconditions.checkState(ggig.isPresent());

            Repository repository = ggig.get();
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
