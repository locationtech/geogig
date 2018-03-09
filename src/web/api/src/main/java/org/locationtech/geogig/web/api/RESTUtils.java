/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;

public class RESTUtils {

    public static String getStringAttribute(final HttpServletRequest request, final String key) {
        Object value = request.getAttribute(key);
        if (value == null) {
            return null;
        }

        try {
            return URLDecoder.decode(value.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void encodeAlternateAtomLink(MediaType format, StreamingWriter w, String href)
            throws StreamWriterException {
        if ("xml".equalsIgnoreCase(format.getSubtype())) {
            w.writeStartElement("atom:link");
            w.writeAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
            w.writeAttribute("rel", "alternate");
            w.writeAttribute("href", href);
            w.writeAttribute("type", format.toString());
            w.writeEndElement();
        } else if (org.springframework.http.MediaType.APPLICATION_JSON.equals(format)) {
            w.writeAttribute("href", href);
        }
    }

    public static String buildHref(String baseURL, String link, MediaType format) {
        link = baseURL + "/" + link;

        // try to figure out extension
        String ext = null;
        if (format != null) {
            ext = format.getSubtype();
        }

        if (ext != null && ext.length() > 0) {
            link = link + "." + ext;
        }

        return link;
    }
}
