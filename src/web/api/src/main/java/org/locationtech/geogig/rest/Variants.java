/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.locationtech.geogig.web.api.RESTUtils;
import org.springframework.http.MediaType;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

public class Variants {
    public static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv");

    public static final MediaType GEOPKG_MEDIA_TYPE;

    static {
        Map<String, String> geopkgParams = Maps.newHashMap();
        geopkgParams.put("type", "geopackage");
        GEOPKG_MEDIA_TYPE = new MediaType(MediaType.APPLICATION_OCTET_STREAM, geopkgParams);
    }
    public static Optional<MediaType> getMediaTypeByExtension(HttpServletRequest request,
            List<MediaType> supported) {
        String extension = RESTUtils.getStringAttribute(request, "extension");
        MediaType t = null;
        if ("xml".equals(extension) && supported.contains(MediaType.APPLICATION_XML)) {
            t = MediaType.APPLICATION_XML;
        } else if ("json".equals(extension) && supported.contains(MediaType.APPLICATION_JSON)) {
            t = MediaType.APPLICATION_JSON;
        } else if ("csv".equals(extension) && supported.contains(CSV_MEDIA_TYPE)) {
            t = CSV_MEDIA_TYPE;
        } else if ("geopkg".equals(extension) && supported.contains(GEOPKG_MEDIA_TYPE)) {
            t = GEOPKG_MEDIA_TYPE;
        } else if ("txt".equals(extension) && supported.contains(MediaType.TEXT_PLAIN)) {
            t = MediaType.TEXT_PLAIN;
        }
        return Optional.fromNullable(t);
    }

}
