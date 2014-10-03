package org.locationtech.geogig.rest;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;

class Representations {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> AsyncCommandRepresentation<T> newRepresentation(AsyncCommand<T> cmd,
            MediaType mediaType, String baseURL) {

        ServiceLoader<CommandRepresentationFactory> serviceLoader;
        serviceLoader = ServiceLoader.load(CommandRepresentationFactory.class);

        CommandRepresentationFactory factory;
        for (Iterator<CommandRepresentationFactory> it = serviceLoader.iterator(); it.hasNext();) {
            factory = it.next();
            if (factory.supports(cmd.getCommandClass())) {
                AsyncCommandRepresentation<T> rep = factory.newRepresentation(cmd, mediaType,
                        baseURL);
                return rep;
            }
        }

        return new SimpleAsyncCommandRepresentation<T>(mediaType, cmd, baseURL);
    }
}
