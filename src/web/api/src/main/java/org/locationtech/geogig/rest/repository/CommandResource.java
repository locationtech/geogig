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

import static org.locationtech.geogig.rest.Variants.CSV;
import static org.locationtech.geogig.rest.Variants.CSV_MEDIA_TYPE;
import static org.locationtech.geogig.rest.Variants.JSON;
import static org.locationtech.geogig.rest.Variants.XML;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;
import static org.locationtech.geogig.web.api.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.rest.RestletException;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandResponseStreamingWriterRepresentation;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamWriterRepresentation;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class CommandResource extends Resource {

    private WebAPICommand command;

    protected WebAPICommand buildCommand(String commandName) {
        return CommandBuilder.build(commandName);
    }

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(XML);
        variants.add(JSON);
        variants.add(CSV);

        final String commandName = getCommandName();
        command = buildCommand(commandName);
        assert command != null;
    }

    protected String getCommandName() {
        return (String) getRequest().getAttributes().get("command");
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    protected ParameterSet getRequestParams() {
        Request request = getRequest();
        ParameterSet params = new FormParams(request.getResourceRef().getQueryAsForm());
        ParameterSet entityParams = handleRequestEntity(request);
        if (entityParams != null) {
            params = ParameterSet.concat(params, entityParams);
        }

        return params;
    }

    protected ParameterSet handleRequestEntity(Request request) {
        ParameterSet entityParams = null;
        if (request.isEntityAvailable()) {
            Representation entity = request.getEntity();
            final MediaType reqMediaType = entity.getMediaType();

            if (MediaType.APPLICATION_WWW_FORM.equals(reqMediaType, true)) {
                // URL encoded form parameters
                try {
                    Form form = request.getEntityAsForm();
                    entityParams = new FormParams(form);
                } catch (Exception ex) {
                    throw new RestletException("Error parsing URL encoded form request",
                            Status.CLIENT_ERROR_BAD_REQUEST, ex);
                }
            } else if (MediaType.APPLICATION_JSON.equals(reqMediaType, true)) {
                // JSON encoded parameters
                try {
                    String jsonRep = entity.getText();
                    JsonObject jsonObj = Json.createReader(new StringReader(jsonRep)).readObject();
                    entityParams = new JsonParams(jsonObj);
                } catch (IOException ex) {
                    throw new RestletException("Error parsing JSON request",
                            Status.CLIENT_ERROR_BAD_REQUEST, ex);
                }
            } else if (MediaType.APPLICATION_XML.equals(reqMediaType, true)) {
                try {
                    String xmlRep = entity.getText();
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    InputSource xmlIn = new InputSource(new StringReader(xmlRep));
                    Document doc = dBuilder.parse(xmlIn);
                    doc.getDocumentElement().normalize();
                    entityParams = new XmlParams(doc);
                } catch (IOException | SAXException | ParserConfigurationException ex) {
                    throw new RestletException("Error parsing XML request",
                            Status.CLIENT_ERROR_BAD_REQUEST, ex);
                }
            } else if (null != reqMediaType) {
                // unsupported MediaType
                throw new RestletException("Unsupported Request MediaType: " + reqMediaType,
                        Status.CLIENT_ERROR_BAD_REQUEST);
            }
            // no parameters specified
        }
        return entityParams;
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

    private Representation processRequest(Method method) {
        Representation representation = null;
        MediaType format = MediaType.TEXT_PLAIN;
        try {
            ParameterSet options = getRequestParams();
            Variant variant = getPreferredVariant();
            format = resolveFormat(options, variant);
            if (checkMethod(command.supports(method), format)) {
                command.setParameters(options);
                representation = runCommand(variant, getRequest(), format);
            }
        } catch (RepositoryBusyException ex) {
            representation = formatBusyException(ex, format);
            getResponse().setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
        } catch (CommandSpecException ex) {
            representation = formatException(ex, format);
            getResponse().setStatus(ex.getStatus());
        } catch (RestletException ex) {
            representation = ex.getRepresentation();
            getResponse().setStatus(ex.getStatus());
        } catch (IllegalArgumentException ex) {
            representation = formatException(ex, format);
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (Exception ex) {
            representation = formatUnexpectedException(ex, format);
            getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
        }
        return representation;
    }

    @Override
    public void put(Representation entity) {
        Representation representation = processRequest(Method.PUT);
        getResponse().setEntity(representation);
    }

    @Override
    public void post(Representation entity) {
        Representation representation = processRequest(Method.POST);
        getResponse().setEntity(representation);
    }

    @Override
    public void delete() {
        Representation representation = processRequest(Method.DELETE);
        getResponse().setEntity(representation);
    }

    /**
     * Handles GET requests, called by {@link #handleGet()}
     * 
     * @return a Representation of this CommandResource request.
     */
    @Override
    public Representation getRepresentation(Variant variant) {
        return processRequest(Method.GET);
    }

    protected ParameterSet buildParameterSet(final Form options) {
        return new FormParams(options);
    }

    protected Optional<Repository> geogig = null;

    protected Representation runCommand(Variant variant, Request request, MediaType outputFormat) {
        geogig = getGeogig(request);
        Preconditions.checkState(geogig.isPresent());
        RestletContext ctx = new RestletContext(geogig.get(), request);
        command.run(ctx);
        Representation rep = ctx.getRepresentation(outputFormat, getJSONPCallback());
        getResponse().setStatus(command.getStatus());

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

    protected MediaType resolveFormat(ParameterSet options, Variant variant) {
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

    static class RestletContext implements CommandContext {

        CommandResponse responseContent = null;

        StreamResponse streamContent = null;

        final Repository geogig;

        private final Request request;

        private Function<MediaType, Representation> representation;

        RestletContext(Repository geogig, Request request) {
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
