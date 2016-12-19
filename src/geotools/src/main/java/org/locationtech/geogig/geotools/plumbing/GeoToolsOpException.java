/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

/**
 * Exception thrown by operations within the GeoTools extension.
 * 
 */
@SuppressWarnings("serial")
public class GeoToolsOpException extends RuntimeException {
    /**
     * Enumeration of possible status codes that indicate what type of exception occurred.
     */
    public enum StatusCode {
        ALL_AND_TABLE_DEFINED, DATASTORE_NOT_DEFINED, TABLE_NOT_DEFINED, NO_FEATURES_FOUND, TABLE_NOT_FOUND, UNABLE_TO_GET_NAMES, UNABLE_TO_GET_FEATURES, UNABLE_TO_INSERT, UNABLE_TO_ADD, CANNOT_CREATE_FEATURESTORE, ALTER_AND_ALL_DEFINED, MIXED_FEATURE_TYPES, INCOMPATIBLE_FEATURE_TYPE
    }

    /**
     * The status code for this exception.
     */
    public StatusCode statusCode;

    /**
     * Construct a new exception with the given status code.
     * 
     * @param statusCode the status code for this exception
     */
    public GeoToolsOpException(StatusCode statusCode) {
        super(statusCode.toString());
        this.statusCode = statusCode;
    }

    public GeoToolsOpException(StatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Construct a new exception with the given cause and status code.
     * 
     * @param e the cause of this exception
     * @param statusCode the status code for this exception
     */
    public GeoToolsOpException(Exception e, StatusCode statusCode) {
        super(statusCode.toString(), e);
        this.statusCode = statusCode;
    }
}
