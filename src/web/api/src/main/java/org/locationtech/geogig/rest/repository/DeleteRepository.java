/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.Variants.JSON;
import static org.locationtech.geogig.rest.Variants.XML;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;

import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.rest.JettisonRepresentation;
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
 * Allows a user to delete a repository.
 */

public class DeleteRepository extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(XML);
        variants.add(JSON);
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    @Override
    public void handleDelete() {
        final Request request = getRequest();
        Variant variant = getPreferredVariant();
        MediaType mediaType = variant.getMediaType();

        Optional<GeoGIG> geogig = RESTUtils.getGeogig(request);
        if (!geogig.isPresent() || geogig.get().getRepository() == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("No repository to delete.")));
            return;
        }

        Form options = request.getResourceRef().getQueryAsForm();
        final String deleteToken = options.getFirstValue("token");
        if (deleteToken == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            getResponse()
                    .setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN, StreamResponse
                    .error("You must specify the correct token to delete a repository.")));
            return;
        }

        final String deleteKey = deleteKeyForToken(deleteToken);

        Optional<byte[]> blobValue = geogig.get().getRepository().blobStore().getBlob(deleteKey);
        if (!blobValue.isPresent()) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            getResponse().setEntity(new StreamWriterRepresentation(MediaType.TEXT_PLAIN,
                    StreamResponse.error("The specified token does not exist or has expired.")));
            return;
        }

        final String repoName = geogig.get().command(ResolveRepositoryName.class).call();

        RepositoryProvider repoProvider = RESTUtils.repositoryProvider(request);

        repoProvider.delete(request);

        final String rootPath = request.getRootRef().toString();
        getResponse().setEntity(new DeleteRepositoryRepresentation(mediaType, rootPath, repoName));
    }

    public static String deleteKeyForToken(String token) {
        return "command/delete/" + token;
    }

    private static class DeleteRepositoryRepresentation extends JettisonRepresentation {

        final String repoName;

        public DeleteRepositoryRepresentation(MediaType mediaType, String baseURL,
                String repoName) {
            super(mediaType, baseURL);
            this.repoName = repoName;
        }

        @Override
        protected void write(XMLStreamWriter w) throws XMLStreamException {
            w.writeStartElement("deleted");
            w.writeCharacters(repoName);
            w.writeEndElement();
        }

    }

}
