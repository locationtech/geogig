/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.rest.StreamingWriterRepresentation;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.restlet.Context;
import org.restlet.Finder;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

/**
 * Creates a new commit on the server with the changes provided by the client.
 */
public class RepositoryFinder extends Finder {

    final RepositoryProvider repoProvider;

    RepositoryFinder(RepositoryProvider repoProvider) {
        this.repoProvider = repoProvider;
    }

    @Override
    public Resource findTarget(Request request, Response response) {
        return new RepositoryListResource(getContext(), request, response);
    }

    private class RepositoryListResource extends Resource {

        public RepositoryListResource(//
                Context context, //
                Request request, //
                Response response) //
        {
            super(context, request, response);
        }

        @Override
        public void init(Context context, Request request, Response response) {
            super.init(context, request, response);
            List<Variant> variants = getVariants();
            // variants.add(XML);
            // variants.add(TEXT_XML);
            // variants.add(JSON);
        }

        @Override
        public Variant getPreferredVariant() {
            return super.getPreferredVariant();
        }

        @Override
        public Representation getRepresentation(Variant variant) {
            final Request request = getRequest();

            MediaType mediaType = variant.getMediaType();
            final String rootPath = request.getRootRef().toString();

            return new RepositoryListRepresentation(mediaType, rootPath);
        }

        private class RepositoryListRepresentation extends StreamingWriterRepresentation {

            public RepositoryListRepresentation(MediaType mediaType, String baseURL) {
                super(mediaType, baseURL);
            }

            @Override
            protected void write(StreamingWriter w) throws StreamWriterException {
                w.writeStartElement("repos");
                Iterator<String> repos = repoProvider.findRepositories();
                w.writeStartArray("repo");
                while (repos.hasNext()) {
                    String repoName = repos.next();
                    w.writeStartArrayElement("repo");
                    w.writeElement("name", repoName);
                    encodeAlternateAtomLink(w,
                            RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repoName);
                    w.writeEndArrayElement();
                }
                w.writeEndArray();
                w.writeEndElement();
            }

        }
    }
}
