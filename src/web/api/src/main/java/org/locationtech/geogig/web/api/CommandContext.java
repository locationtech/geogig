/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.function.Function;

import org.locationtech.geogig.repository.GeoGIG;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.resource.Representation;

/**
 *
 */
public interface CommandContext {

    String getBaseURL();

    /**
     * @return the {@link GeoGIG} for this context.
     */
    GeoGIG getGeoGIG();

    Method getMethod();

    /**
     * Sets the response for the context.
     * 
     * @param responseContent the command response
     */
    void setResponseContent(CommandResponse responseContent);

    /**
     * Sets the response for the context.
     * 
     * @param responseContent the command response
     */
    void setResponseContent(StreamResponse responseContent);

    void setResponse(Function<MediaType, Representation> representation);

    RepositoryProvider getRepositoryProvider();

}
