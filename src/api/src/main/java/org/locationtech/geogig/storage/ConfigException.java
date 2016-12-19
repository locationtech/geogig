/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage;

/**
 * Exception thrown by ConfigDatabase that contains the error status code.
 * 
 * @since 1.0
 */
@SuppressWarnings("serial")
public class ConfigException extends RuntimeException {
    /**
     * Possible status codes for Config exceptions.
     */
    public enum StatusCode {
        INVALID_LOCATION, CANNOT_WRITE, SECTION_OR_NAME_NOT_PROVIDED, SECTION_OR_KEY_INVALID, OPTION_DOES_NOT_EXIST, MULTIPLE_OPTIONS_MATCH, INVALID_REGEXP, USERHOME_NOT_SET, TOO_MANY_ACTIONS, MISSING_SECTION, TOO_MANY_ARGS
    }

    /**
     * The status code for this exception.
     */
    public StatusCode statusCode;

    /**
     * Constructs a new {@code ConfigException} with the given status code.
     * 
     * @param statusCode the status code for this exception
     */
    public ConfigException(StatusCode statusCode) {
        this(null, statusCode);
    }

    /**
     * Construct a new exception with the given cause and status code.
     * 
     * @param e the cause of this exception
     * @param statusCode the status code for this exception
     */
    public ConfigException(Exception e, StatusCode statusCode) {
        super(e);
        this.statusCode = statusCode;
    }
}
