/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.Variants.*;
import static org.locationtech.geogig.web.api.RESTUtils.getGeogig;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.rest.RestletException;
import org.locationtech.geogig.web.api.*;
import org.restlet.Context;
import org.restlet.data.*;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class CommandResource extends Resource {

    protected Form options;

    protected WebAPICommand command;

    protected WebAPICommand buildCommand(String commandName, ParameterSet params) {
        return CommandBuilder.build(commandName, params);
    }

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(XML);
        variants.add(JSON);
        variants.add(CSV);

        final String commandName = getCommandName();

        options = getRequest().getResourceRef().getQueryAsForm();
        ParameterSet params = buildParameterSet(options);
        command = buildCommand(commandName, params);
        assert command != null;
    }

    protected Form getOptions() {
        return options;
    }

    protected String getCommandName() {
        return (String) getRequest().getAttributes().get("command");
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    @Override
    public boolean allowPost() {
        return command.supports(Method.POST);
    }

    @Override
    public boolean allowPut() {
        return command.supports(Method.PUT);
    }

    @Override
    public boolean allowGet() {
        return command.supports(Method.GET);
    }

    @Override
    public boolean allowDelete() {
        return command.supports(Method.DELETE);
    }

    private boolean checkMethod(boolean allowed, MediaType format) {
        if (!allowed) {
            getResponse().setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            formatException(new CommandSpecException(
                    "The request method is unsupported for this operation."), format);
        }
        return allowed;
    }

    @Override
    public void put(Representation entity) {
        Variant variant = getPreferredVariant();
        MediaType format = resolveFormat(options, variant);
        if (!checkMethod(allowPut(), format)) {
            return;
        }
        Representation representation = runCommand(variant, getRequest());
        getResponse().setEntity(representation);
    }

    @Override
    public void post(Representation entity) {
        Variant variant = getPreferredVariant();
        MediaType format = resolveFormat(options, variant);
        if (!checkMethod(allowPost(), format)) {
            return;
        }
        Representation representation = runCommand(variant, getRequest());
        getResponse().setEntity(representation);
    }

    @Override
    public void delete() {
        Variant variant = getPreferredVariant();
        MediaType format = resolveFormat(options, variant);
        if (!checkMethod(allowDelete(), format)) {
            return;
        }
        Representation representation = runCommand(variant, getRequest());
        getResponse().setEntity(representation);
    }

    /**
     * Handles GET requests, called by {@link #handleGet()}
     * 
     * @return a Representation of this CommandResource request.
     */
    @Override
    public Representation getRepresentation(Variant variant) {
        Request request = getRequest();
        Representation representation = runCommand(variant, request);
        return representation;
    }

    protected ParameterSet buildParameterSet(final Form options) {
        return new FormParams(options);
    }

    protected Optional<Repository> geogig = null;

    protected Representation runCommand(Variant variant, Request request) {
        Representation rep;
        MediaType format = resolveFormat(options, variant);
        try {
            geogig = getGeogig(request);
            Preconditions.checkState(geogig.isPresent());
            RestletContext ctx = new RestletContext(geogig.get(), request);
            command.run(ctx);
            rep = ctx.getRepresentation(format, getJSONPCallback());
            getResponse().setStatus(command.getStatus());
        } catch (RepositoryBusyException ex) {
            rep = formatBusyException(ex, format);
            getResponse().setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
        } catch (CommandSpecException ex) {
            rep = formatException(ex, format);
            getResponse().setStatus(ex.getStatus());
        } catch (RestletException ex) {
            rep = ex.getRepresentation();
            getResponse().setStatus(ex.getStatus());
        } catch (IllegalArgumentException ex) {
            rep = formatException(ex, format);
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (Exception ex) {
            rep = formatUnexpectedException(ex, format);
            getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
        }

        return rep;
    }

    private Representation formatBusyException(RepositoryBusyException ex, MediaType format) {
        Logger logger = getLogger();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "RepositoryBusyException", ex);
        }
        if (format == CSV_MEDIA_TYPE) {
            return new StreamWriterRepresentation(format, StreamResponse.error(ex.getMessage()));
        }
        return new CommandResponseStreamingWriterRepresentation(format,
                CommandResponse.error(ex.getMessage()), getJSONPCallback());
    }

    private Representation formatException(IllegalArgumentException ex, MediaType format) {
        Logger logger = getLogger();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "CommandSpecException", ex);
        }
        if (format == CSV_MEDIA_TYPE) {
            return new StreamWriterRepresentation(format, StreamResponse.error(ex.getMessage()));
        }
        return new CommandResponseStreamingWriterRepresentation(format,
                CommandResponse.error(ex.getMessage()), getJSONPCallback());

    }

    private Representation formatUnexpectedException(Exception ex, MediaType format) {
        Logger logger = getLogger();
        UUID uuid = UUID.randomUUID();
        String stack = "";
        StackTraceElement[] trace = ex.getStackTrace();
        for (int index = 0; index < 5; index++) {
            if (index < trace.length) {
                stack += trace[index].toString() + '\t';
            } else {
                break;
            }
        }
        logger.log(Level.SEVERE, "Unexpected exception : " + uuid, ex);
        if (format == CSV_MEDIA_TYPE) {
            return new StreamWriterRepresentation(format, StreamResponse.error(stack));
        }
        return new CommandResponseStreamingWriterRepresentation(format, CommandResponse.error(stack),
                getJSONPCallback());
    }

    private String getJSONPCallback() {
        Form form = getRequest().getResourceRef().getQueryAsForm();
        return form.getFirstValue("callback", null);
    }

    protected MediaType resolveFormat(Form options, Variant variant) {
        MediaType retval = variant.getMediaType();
        String requested = options.getFirstValue("output_format");
        if (requested != null) {
            if (requested.equalsIgnoreCase("xml")) {
                retval = MediaType.APPLICATION_XML;
            } else if (requested.equalsIgnoreCase("json")) {
                retval = MediaType.APPLICATION_JSON;
            } else if (requested.equalsIgnoreCase("csv")) {
                retval = CSV_MEDIA_TYPE;
            } else {
                throw new RestletException("Invalid output_format '" + requested + "'",
                        org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }
        return retval;
    }

    public static class RestletContext implements CommandContext {

        CommandResponse responseContent = null;

        StreamResponse streamContent = null;

        final Repository geogig;

        private final Request request;

        private Function<MediaType, Representation> representation;

        public RestletContext(Repository geogig, Request request) {
            this.geogig = geogig;
            this.request = request;
        }

        @Override
        public Repository getRepository() {
            return geogig;
        }

        @Override
        public org.locationtech.geogig.repository.Context context() {
            return geogig.context();
        }

        @Override
        public Method getMethod() {
            return request.getMethod();
        }

        public Representation getRepresentation(MediaType format, String callback) {
            if (representation != null) {
                return representation.apply(format);
            }
            if (streamContent != null) {
                if (format != CSV_MEDIA_TYPE) {
                    throw new CommandSpecException(
                            "Unsupported Media Type: This response is only compatible with text/csv.");
                }
                return new StreamWriterRepresentation(format, streamContent);
            }
            if (format != MediaType.APPLICATION_JSON && format != MediaType.APPLICATION_XML) {
                throw new CommandSpecException(
                        "Unsupported Media Type: This response is only compatible with application/json and application/xml.");
            }
            return new CommandResponseStreamingWriterRepresentation(format, responseContent, callback);
        }

        @Override
        public void setResponse(Function<MediaType, Representation> representation) {
            this.representation = representation;
        }

        @Override
        public void setResponseContent(CommandResponse responseContent) {
            this.responseContent = responseContent;
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            this.streamContent = responseContent;
        }

        @Override
        public String getBaseURL() {
            return request.getRootRef().toString();
        }

        @Override
        public RepositoryProvider getRepositoryProvider() {
            return RESTUtils.repositoryProvider(request);
        }

    }
}
