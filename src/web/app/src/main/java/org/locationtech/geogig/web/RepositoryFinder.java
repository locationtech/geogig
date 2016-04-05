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

import static org.locationtech.geogig.rest.Variants.JSON;
import static org.locationtech.geogig.rest.Variants.TEXT_XML;
import static org.locationtech.geogig.rest.Variants.XML;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;

import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.rest.JettisonRepresentation;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
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
            variants.add(XML);
            variants.add(TEXT_XML);
            variants.add(JSON);
        }

        @Override
        public Variant getPreferredVariant() {
            return getVariantByExtension(getRequest(), getVariants())
                    .or(super.getPreferredVariant());
        }

        @Override
        public Representation getRepresentation(Variant variant) {
            final Request request = getRequest();

            MediaType mediaType = variant.getMediaType();
            final String rootPath = request.getRootRef().toString();

            return new RepositoryListRepresentation(mediaType, rootPath);
        }

        private class RepositoryListRepresentation extends JettisonRepresentation {

            public RepositoryListRepresentation(MediaType mediaType, String baseURL) {
                super(mediaType, baseURL);
            }

            @Override
            protected void write(XMLStreamWriter w) throws XMLStreamException {
                w.writeStartElement("repos");
                Iterator<String> repos = repoProvider.findRepositories();
                while (repos.hasNext()) {
                    String repoName = repos.next();
                    w.writeStartElement("repo");
                    w.writeStartElement("name");
                    w.writeCharacters(repoName);
                    w.writeEndElement();
                    encodeAlternateAtomLink(w,
                            RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repoName);
                    w.writeEndElement();
                }
                w.writeEndElement();
            }

        }
    }
}
