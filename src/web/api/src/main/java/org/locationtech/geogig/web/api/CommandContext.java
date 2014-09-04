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

import org.locationtech.geogig.api.GeoGIG;

/**
 *
 */
public interface CommandContext {

    /**
     * @return the {@link GeoGIG} for this context.
     */
    GeoGIG getGeoGIG();

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

}
