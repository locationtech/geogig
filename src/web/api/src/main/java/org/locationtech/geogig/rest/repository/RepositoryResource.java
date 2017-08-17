/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.net.URI;

import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.StreamingWriterRepresentation;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamWriterRepresentation;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

/**
 * Access point to a single repository. Provides a Repository information response for <b>GET</b>
 * requests. Performs a Repository delete operation for <b>DELETE</b> requests.
 */
public class RepositoryResource extends DeleteRepository {

    @Override
    public Representation getRepresentation(Variant variant) {
        final Request request = getRequest();
        RepositoryProvider provider = RESTUtils.repositoryProvider(request);

        Optional<Repository> geogig = Optional.absent();// provider.getGeogig(request);
        if (!geogig.isPresent() || !geogig.get().isOpen()) {
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("Repository not found."));
        }
        final Repository repository = geogig.get();
        final String repoName = repository.command(ResolveRepositoryName.class).call();
        final URI location = repository.getLocation().normalize();
        final String baseURL = getRequest().getRootRef().toString();
        return new RepositorytRepresentation(variant.getMediaType(), baseURL, repoName, location);
    }

    private static class RepositorytRepresentation extends StreamingWriterRepresentation {

        private final String repoName;
        private final URI location;

        public RepositorytRepresentation(MediaType mediaType, String baseURL, String repoName, URI location) {
            super(mediaType, baseURL);
            this.repoName = repoName;
            this.location = location;
        }

        @Override
        public void write(StreamingWriter w) throws StreamWriterException {
            w.writeStartElement("repository");
            w.writeElement("name", repoName);
            w.writeElement("location", location);
            w.writeEndElement();
        }
    }
}
