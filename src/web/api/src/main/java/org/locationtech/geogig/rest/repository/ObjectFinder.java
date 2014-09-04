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
import java.io.OutputStream;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.restlet.Context;
import org.restlet.Finder;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Expects an {@code id} request attribute containing the string representation of an
 * {@link ObjectId} (40 char hex string) to look up for in the repository database and return it as
 * a plain byte stream.
 */
public class ObjectFinder extends Finder {

    @Override
    public Resource findTarget(Request request, Response response) {

        if (request.getAttributes().containsKey("id")) {
            final Optional<GeoGIG> ggit = getGeogig(request);
            Preconditions.checkState(ggit.isPresent());

            final String id = (String) request.getAttributes().get("id");
            final ObjectId oid = ObjectId.valueOf(id);

            GeoGIG geogig = ggit.get();
            Repository repository = geogig.getRepository();
            boolean blobExists = repository.blobExists(oid);
            if (blobExists) {
                ObjectResource objectResource = new ObjectResource(oid, geogig);
                objectResource.init(getContext(), request, response);
                return objectResource;
            }
        }

        return super.findTarget(request, response);
    }

    private static class ObjectResource extends Resource {

        private ObjectId oid;

        private GeoGIG geogig;

        public ObjectResource(ObjectId oid, GeoGIG geogig) {
            this.oid = oid;
            this.geogig = geogig;
        }

        @Override
        public void init(Context context, Request request, Response response) {
            super.init(context, request, response);
            List<Variant> variants = getVariants();

            variants.add(new RevObjectBinaryRepresentation(oid, geogig));
        }
    }

    private static class RevObjectBinaryRepresentation extends OutputRepresentation {
        private final ObjectId oid;

        private static final ObjectSerializingFactory serialFac = DataStreamSerializationFactoryV1.INSTANCE;

        private final GeoGIG ggit;

        public RevObjectBinaryRepresentation(ObjectId oid, GeoGIG ggit) {
            super(MediaType.APPLICATION_OCTET_STREAM);
            this.oid = oid;
            this.ggit = ggit;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            Repository repository = ggit.getRepository();
            RevObject rawObject = repository.objectDatabase().get(oid);
            serialFac.createObjectWriter(rawObject.getType()).write(rawObject, out);
        }
    }

}
