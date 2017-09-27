/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.google.common.collect.Lists;

@ControllerAdvice
public class RequestExceptionHandler extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestExceptionHandler.class);

    @ExceptionHandler({ RepositoryBusyException.class })
    public void handleRepositoryBusyException(RepositoryBusyException ex,
            HttpServletRequest request, HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        buildResponse(ex, request, response, headers, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({ CommandSpecException.class })
    public void handleCommandSpecException(CommandSpecException ex, HttpServletRequest request,
            HttpServletResponse response) {
        HttpHeaders headers = updateAllowedMethodsFromException(new HttpHeaders(), ex);
        buildResponse(ex, request, response, headers, ex.getStatus());
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    public void handleIllegalArgumentException(IllegalArgumentException ex,
            HttpServletRequest request, HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        buildResponse(ex, request, response, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ Exception.class })
    public void handleException(Exception ex, HttpServletRequest request,
            HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        buildResponse(ex, request, response, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class })
    public void handleException(HttpMessageNotReadableException ex, HttpServletRequest request,
            HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        buildResponse(ex, request, response, headers, HttpStatus.BAD_REQUEST);
    }

    private void buildResponse(Exception ex, HttpServletRequest request,
            HttpServletResponse response,
            HttpHeaders headers, HttpStatus status) {
        for (Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                response.addHeader(entry.getKey(), value);
            }
        }
        try {
            encode(new ExceptionResponse(ex, status), request, response);
        } catch (Exception ex2) {
            // trying to write out an Exception threw an Exception....
            encodeError(ex2, request, response);
        }
    }
    protected final void encodeError(Exception ex, final HttpServletRequest request,
            final HttpServletResponse response) {
        // set the Content-Type
        response.setContentType(MediaType.TEXT_PLAIN.toString());
        // set the status
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        // write the LegacyResponse object out to the Response stream
        try (Writer writer = response.getWriter()) {
            writer.write(ex.getLocalizedMessage());
        } catch (IOException e) {
            throw new CommandSpecException("Error writing response",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private HttpHeaders updateAllowedMethodsFromException(HttpHeaders headers,
            CommandSpecException ex) {
        if (ex.getAllowedMethods() != null) {
            // headers set on exception, make a list of allowed HttpMethods
            List<HttpMethod> methodList = Lists.newArrayList();
            // ge the list of names from the exception
            Set<String> allowedMethods = ex.getAllowedMethods();
            for (String method : allowedMethods) {
                methodList.add(HttpMethod.resolve(method));
            }
            // set the allowed methods on the headers
            headers.setAllow(EnumSet.copyOf(methodList));
        }
        return headers;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @XmlRootElement(name = "response")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ExceptionResponse extends LegacyResponse {
        @XmlElement
        boolean success = false;

        @XmlElement
        String error;

        HttpStatus status;

        public ExceptionResponse(HttpStatus status) {
            this.error = "";
            this.status = status;
        }

        public ExceptionResponse(Exception ex, HttpStatus status) {
            this.error = ex.getMessage();
            this.status = status;
        }

        @Override
        public MediaType resolveMediaType(MediaType suggested) {
            if (MediaType.APPLICATION_JSON.isCompatibleWith(suggested)
                    || MediaType.APPLICATION_XML.isCompatibleWith(suggested)) {
                return suggested;
            }
            return MediaType.APPLICATION_XML;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        protected void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
            writer.writeStartElement("response");
            writer.writeElement("success", success);
            if (error != null && !error.isEmpty()) {
                writer.writeElement("error", error);
            }
            writer.writeEndElement();
        }
    }
}
