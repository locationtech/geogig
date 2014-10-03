package org.locationtech.geogig.rest;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;
import org.restlet.resource.Representation;

/**
 * SPI interface to lookup {@link Representation} factories for concrete {@link AbstractGeoGigOp}
 * classes.
 *
 */
public interface CommandRepresentationFactory<R> {

    public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass);

    public AsyncCommandRepresentation<R> newRepresentation(AsyncCommand<R> cmd,
            MediaType mediaType, String baseURL);
}
