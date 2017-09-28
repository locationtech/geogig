/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes;

/**
 * Exception thrown by the push process.
 * 
 */
@SuppressWarnings("serial")
public class SynchronizationException extends RuntimeException {
    /**
     * Possible status codes for Push exceptions.
     */
    public enum StatusCode {
        /**
         * The branches are equal, no need to push/my last commit is the common ancestor, the remote
         * already has my data.
         */
        NOTHING_TO_PUSH,
        /**
         * There is no common ancestor, a push will overwrite history; or the remote branch's latest
         * commit is not my ancestor, a push will cause a loss of history.
         */
        REMOTE_HAS_CHANGES, HISTORY_TOO_SHALLOW, CANNOT_PUSH_TO_SYMBOLIC_REF
    }

    public StatusCode statusCode;

    /**
     * Constructs a new {@code PushException} with the given status code.
     * 
     * @param statusCode the status code for this exception
     */
    public SynchronizationException(StatusCode statusCode) {
        this(null, statusCode);
    }

    /**
     * Construct a new exception with the given cause and status code.
     * 
     * @param e the cause of this exception
     * @param statusCode the status code for this exception
     */
    public SynchronizationException(Exception e, StatusCode statusCode) {
        super(e);
        this.statusCode = statusCode;
    }
}
