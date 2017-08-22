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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.google.common.collect.Lists;

@ControllerAdvice
public class RequestExceptionHandler extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestExceptionHandler.class);

    @ExceptionHandler({ RepositoryBusyException.class })
    public ResponseEntity<Object> handleRepositoryBusyException(RepositoryBusyException ex,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        return new ResponseEntity<>(new ExceptionResponse(ex), headers,
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({ CommandSpecException.class })
    public ResponseEntity<Object> handleCommandSpecException(CommandSpecException ex,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (this.getMediaType(request).isCompatibleWith(MediaType.APPLICATION_JSON)) {
            // JSON response, use JsonExceptionFormat
            return new ResponseEntity<>(new JsonExceptionResponse(ex),
                    updateAllowedMethodsFromException(headers, ex), ex.getStatus());
        }
        return new ResponseEntity<>(new ExceptionResponse(ex),
                updateAllowedMethodsFromException(headers, ex), ex.getStatus());
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        return new ResponseEntity<>(new ExceptionResponse(ex), headers,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ Exception.class })
    public ResponseEntity<Object> handleException(Exception ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(new ExceptionResponse(ex), headers,
                HttpStatus.INTERNAL_SERVER_ERROR);
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
    public static class ExceptionResponse {
        @XmlElement
        boolean success = false;

        @XmlElement
        String error;

        public ExceptionResponse() {
            this.error = "";
        }

        public ExceptionResponse(Exception ex) {
            this.error = ex.getMessage();
        }
    }

    public static class JsonExceptionResponse {

        private Response response;

        public JsonExceptionResponse() {
            this.response = new Response();
        }

        public JsonExceptionResponse(Exception ex) {
            this.response = new Response(ex);
        }

        public static class Response {

            private final boolean success = false;

            private final String error;

            public Response() {
                this.error = "";
            }

            public Response(Exception ex) {
                this.error = ex.getLocalizedMessage();
            }
        }
    }
}
