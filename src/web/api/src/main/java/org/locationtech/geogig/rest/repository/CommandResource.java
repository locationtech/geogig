/* Copyright (c) 2014 Boundless and others.
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
import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLStreamWriter;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.rest.RestletException;
import org.locationtech.geogig.rest.WriterRepresentation;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class CommandResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(XML);
        variants.add(JSON);
        variants.add(CSV);
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    @Override
    public Representation getRepresentation(Variant variant) {
        Request request = getRequest();
        Representation representation = runCommand(variant, request);
        return representation;
    }

    private Representation runCommand(Variant variant, Request request) {

        final Optional<GeoGIG> geogig = getGeogig(request);
        Preconditions.checkState(geogig.isPresent());

        Representation rep = null;
        WebAPICommand command = null;
        Form options = getRequest().getResourceRef().getQueryAsForm();
        String commandName = (String) getRequest().getAttributes().get("command");
        MediaType format = resolveFormat(options, variant);
        try {
            ParameterSet params = new FormParams(options);
            command = CommandBuilder.build(commandName, params);
            assert command != null;
        } catch (CommandSpecException ex) {
            rep = formatException(ex, format);
        }
        try {
            if (command != null) {
                RestletContext ctx = new RestletContext(geogig.get());
                command.run(ctx);
                rep = ctx.getRepresentation(format, getJSONPCallback());
            }
        } catch (IllegalArgumentException ex) {
            rep = formatException(ex, format);
        } catch (Exception ex) {
            rep = formatUnexpectedException(ex, format);
        }
        return rep;
    }

    private Representation formatException(IllegalArgumentException ex, MediaType format) {
        Logger logger = getLogger();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "CommandSpecException", ex);
        }
        if (format == CSV_MEDIA_TYPE) {
            return new StreamWriterRepresentation(format, StreamResponse.error(ex.getMessage()));
        }
        return new JettisonRepresentation(format, CommandResponse.error(ex.getMessage()),
                getJSONPCallback());

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
        return new JettisonRepresentation(format, CommandResponse.error(stack), getJSONPCallback());
    }

    private String getJSONPCallback() {
        Form form = getRequest().getResourceRef().getQueryAsForm();
        return form.getFirstValue("callback", null);
    }

    private MediaType resolveFormat(Form options, Variant variant) {
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

        final GeoGIG geogig;

        RestletContext(GeoGIG geogig) {
            this.geogig = geogig;
        }

        @Override
        public GeoGIG getGeoGIG() {
            return geogig;
        }

        Representation getRepresentation(MediaType format, String callback) {
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
            return new JettisonRepresentation(format, responseContent, callback);
        }

        @Override
        public void setResponseContent(CommandResponse responseContent) {
            this.responseContent = responseContent;
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            this.streamContent = responseContent;
        }
    }

    public static class JettisonRepresentation extends WriterRepresentation {

        final CommandResponse impl;

        String callback;

        public JettisonRepresentation(MediaType mediaType, CommandResponse impl, String callback) {
            super(mediaType);
            this.impl = impl;
            this.callback = callback;
        }

        private XMLStreamWriter createWriter(Writer writer) {
            final MediaType mediaType = getMediaType();
            XMLStreamWriter xml;
            if (mediaType.getSubType().equalsIgnoreCase("xml")) {
                try {
                    xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
                } catch (XMLStreamException ex) {
                    throw new RuntimeException(ex);
                }
                callback = null; // this doesn't make sense
            } else if (mediaType == MediaType.APPLICATION_JSON) {
                xml = new MappedXMLStreamWriter(new MappedNamespaceConvention(), writer);
            } else {
                throw new RuntimeException("mediatype not handled " + mediaType);
            }
            return xml;
        }

        @Override
        public void write(Writer writer) throws IOException {
            XMLStreamWriter stax = null;
            if (callback != null) {
                writer.write(callback);
                writer.write('(');
            }
            try {
                stax = createWriter(writer);
                impl.write(new ResponseWriter(stax));
                stax.flush();
                stax.close();
            } catch (Exception ex) {
                throw new IOException(ex);
            }
            if (callback != null) {
                writer.write(");");
            }
        }
    }

    static class StreamWriterRepresentation extends WriterRepresentation {

        final StreamResponse impl;

        public StreamWriterRepresentation(MediaType mediaType, StreamResponse impl) {
            super(mediaType);
            this.impl = impl;
        }

        @Override
        public void write(Writer writer) throws IOException {
            try {
                impl.write(writer);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
