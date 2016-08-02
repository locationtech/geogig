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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.repository.Repository;
import org.restlet.data.MediaType;
import org.restlet.data.Request;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class RESTUtils {

    public static Optional<Repository> getGeogig(Request request) {
        RepositoryProvider provider = repositoryProvider(request);
        Optional<Repository> geogig = provider.getGeogig(request);
        return geogig;
    }

    public static RepositoryProvider repositoryProvider(Request request) {
        Object provider = request.getAttributes().get(RepositoryProvider.KEY);
        Preconditions.checkNotNull(provider,
                "No RepositoryProvider found in request attributes under the key %s",
                RepositoryProvider.KEY);
        Preconditions.checkState(provider instanceof RepositoryProvider,
                "Request attribute %s is not of type RepositoryProvider: %s",
                RepositoryProvider.KEY, provider.getClass());
        return (RepositoryProvider) provider;
    }

    public static String getStringAttribute(final Request request, final String key) {
        Object value = request.getAttributes().get(key);
        if (value == null) {
            return null;
        }

        try {
            return URLDecoder.decode(value.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
    }

    public static void encodeAlternateAtomLink(MediaType format, XMLStreamWriter w, String href)
            throws XMLStreamException {
        if (MediaType.TEXT_XML.equals(format) || MediaType.APPLICATION_XML.equals(format)) {
            w.writeStartElement("atom:link");
            w.writeAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
            w.writeAttribute("rel", "alternate");
            w.writeAttribute("href", href);
            if (format != null) {
                w.writeAttribute("type", format.toString());
            }
            w.writeEndElement();
        } else if (MediaType.APPLICATION_JSON.equals(format)) {
            w.writeStartElement("href");
            w.writeCharacters(href);
            w.writeEndElement();
        }
    }

    public static String buildHref(String baseURL, String link, MediaType format) {
        link = baseURL + "/" + link;

        // try to figure out extension
        String ext = null;
        if (format != null) {
            ext = format.getSubType();
        }

        if (ext != null && ext.length() > 0) {
            link = link + "." + ext;
        }

        return link;
    }
}
