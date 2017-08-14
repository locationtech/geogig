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

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class RequestExceptionHandler {

    @ExceptionHandler({ RepositoryBusyException.class })
    public ResponseEntity<Object> handleRepositoryBusyException(RepositoryBusyException ex,
            HttpServletRequest request) {
        return new ResponseEntity<Object>(new ExceptionResponse(ex), new HttpHeaders(),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({ CommandSpecException.class })
    public ResponseEntity<Object> handleCommandSpecException(CommandSpecException ex,
            HttpServletRequest request) {
        return new ResponseEntity<Object>(new ExceptionResponse(ex),
                new HttpHeaders(), ex.getStatus());
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex,
            HttpServletRequest request) {
        return new ResponseEntity<Object>(new ExceptionResponse(ex), new HttpHeaders(),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ Exception.class })
    public ResponseEntity<Object> handleException(Exception ex, HttpServletRequest request) {
        return new ResponseEntity<Object>(new ExceptionResponse(ex), new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR);
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
}
