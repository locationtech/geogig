/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

/**
 * Exception thrown by the {@link CheckoutOp checkout} op.
 * <p>
 * 
 * @TODO: define and codify the possible causes for a checkout to fail
 */
@SuppressWarnings("serial")
public class CheckoutException extends RuntimeException {

    public enum StatusCode {
        LOCAL_CHANGES_NOT_COMMITTED {
            public String message() {
                return "Doing a checkout without a clean working tree and index is currently unsupported.";
            }
        },
        UNMERGED_PATHS {
            public String message() {
                return "There are unmerged paths.";
            }
        };

        public abstract String message();
    }

    public StatusCode statusCode;

    public CheckoutException(String msg, StatusCode statusCode) {
        super(msg);
        this.statusCode = statusCode;
    }

    public CheckoutException(StatusCode statusCode) {
        super(statusCode.message());
        this.statusCode = statusCode;
    }

}
