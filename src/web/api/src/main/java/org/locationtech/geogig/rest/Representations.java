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

import java.util.Iterator;
import java.util.ServiceLoader;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;

public class Representations {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> AsyncCommandRepresentation<T> newRepresentation(AsyncCommand<T> cmd,
            MediaType mediaType, String baseURL, boolean cleanup) {

        ServiceLoader<CommandRepresentationFactory> serviceLoader;
        serviceLoader = ServiceLoader.load(CommandRepresentationFactory.class);

        CommandRepresentationFactory factory;
        for (Iterator<CommandRepresentationFactory> it = serviceLoader.iterator(); it.hasNext();) {
            factory = it.next();
            if (factory.supports(cmd.getCommandClass())) {
                AsyncCommandRepresentation<T> rep = factory.newRepresentation(cmd, mediaType,
                        baseURL, cleanup);
                return rep;
            }
        }

        return new SimpleAsyncCommandRepresentation<T>(mediaType, cmd, baseURL, cleanup);
    }
}
