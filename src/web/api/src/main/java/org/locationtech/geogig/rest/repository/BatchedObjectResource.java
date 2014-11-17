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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.CreateDeduplicator;
import org.locationtech.geogig.remote.BinaryPackedObjects;
import org.locationtech.geogig.remote.ObjectFunnel;
import org.locationtech.geogig.remote.ObjectFunnels;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.Deduplicator;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.restlet.Context;
import org.restlet.Finder;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.io.CountingOutputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Takes a set of commit Ids and packs up their contents into a binary stream to send to the client.
 */
public class BatchedObjectResource extends Finder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchedObjectResource.class);

    @Override
    public Resource findTarget(Request request, Response response) {
        return new ObjectResource(getContext(), request, response);
    }

    private static class ObjectResource extends Resource {
        public ObjectResource(//
                Context context, //
                Request request, //
                Response response) //
        {
            super(context, request, response);
        }

        @Override
        public boolean allowPost() {
            return true;
        }

        @Override
        public void post(Representation entity) {
            InputStream inStream;
            try {
                inStream = entity.getStream();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            final Reader body = new InputStreamReader(inStream);
            final JsonParser parser = new JsonParser();
            final JsonElement messageJson = parser.parse(body);
            LOGGER.info("Serving request to send objects based on message {}", messageJson);
            final List<ObjectId> want = new ArrayList<ObjectId>();
            final List<ObjectId> have = new ArrayList<ObjectId>();

            if (messageJson.isJsonObject()) {
                final JsonObject message = messageJson.getAsJsonObject();
                final JsonArray wantArray;
                final JsonArray haveArray;
                if (message.has("want") && message.get("want").isJsonArray()) {
                    wantArray = message.get("want").getAsJsonArray();
                } else {
                    wantArray = new JsonArray();
                }
                if (message.has("have") && message.get("have").isJsonArray()) {
                    haveArray = message.get("have").getAsJsonArray();
                } else {
                    haveArray = new JsonArray();
                }
                for (final JsonElement e : wantArray) {
                    if (e.isJsonPrimitive()) {
                        want.add(ObjectId.valueOf(e.getAsJsonPrimitive().getAsString()));
                    }
                }
                for (final JsonElement e : haveArray) {
                    if (e.isJsonPrimitive()) {
                        have.add(ObjectId.valueOf(e.getAsJsonPrimitive().getAsString()));
                    }
                }
            }

            Request request = getRequest();
            final GeoGIG ggit = getGeogig(request).get();
            final Repository repository = ggit.getRepository();
            final Deduplicator deduplicator = ggit.command(CreateDeduplicator.class).call();

            BinaryPackedObjects packer = new BinaryPackedObjects(repository.stagingDatabase());
            Representation rep = new RevObjectBinaryRepresentation(packer, want, have, deduplicator);
            Response response = getResponse();
            response.setEntity(rep);
        }
    }

    private static class RevObjectBinaryRepresentation extends OutputRepresentation {
        private final BinaryPackedObjects packer;

        private final List<ObjectId> want;

        private final List<ObjectId> have;

        private Deduplicator deduplicator;

        public RevObjectBinaryRepresentation( //
                BinaryPackedObjects packer, //
                List<ObjectId> want, //
                List<ObjectId> have, //
                Deduplicator deduplicator) //
        {
            super(MediaType.APPLICATION_OCTET_STREAM);
            this.packer = packer;
            this.want = want;
            this.have = have;
            this.deduplicator = deduplicator;
        }

        @Override
        public void write(final OutputStream out) throws IOException {
            CountingOutputStream counting = new CountingOutputStream(out);
            OutputStream output = counting;
            try {
                ObjectFunnel funnel;
                funnel = ObjectFunnels.newFunnel(output, DataStreamSerializationFactoryV1.INSTANCE);
                packer.write(funnel, want, have, false, deduplicator);
                counting.flush();
                funnel.close();
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            } finally {
                deduplicator.release();
            }
        }
    }
}
