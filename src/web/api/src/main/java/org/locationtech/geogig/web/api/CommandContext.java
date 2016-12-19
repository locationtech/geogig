/* Copyright (c) 2013-2016 Boundless and others.
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

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.resource.Representation;

/**
 *
 */
public interface CommandContext {

    String getBaseURL();

    Context context();

    /**
     * @return the {@link Repository} for this context, or {@code null} if not set.
     */
    Repository getRepository();

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
