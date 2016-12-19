/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.Writer;

/**
 * Provides a base abstract streaming response implementation for Web API commands.
 */
public abstract class StreamResponse {

    /**
     * Write the command response to the provided {@link Writer}.
     * 
     * @param out the output stream
     * @throws Exception
     */
    public abstract void write(Writer out) throws Exception;

    /**
     * @param message the warning message
     * @return a {@code StreamResponse} with the given warning message
     */
    public static StreamResponse warning(String message) {
        return new ErrorLiteral("warning", message);
    }

    /**
     * @param message the error message
     * @return a {@code StreamResponse} with the given error message
     */
    public static StreamResponse error(String message) {
        return new ErrorLiteral("error", message);
    }

    /**
     * Provides a command response error implementation. This can be used for both errors and
     * warnings.
     */
    static class ErrorLiteral extends StreamResponse {
        private final String[] items;

        /**
         * Constructs a new {@code ErrorLiteral} with the given array of items. Items should be
         * ordered in key value pairs. For example [key, value, key, value].
         * 
         * @param items the key value pairs for this error
         */
        public ErrorLiteral(String... items) {
            this.items = items;
        }

        /**
         * Write the error response to the provided {@link ResponseWriter}.
         * 
         * @param out the output stream
         * @throws Exception
         */
        @Override
        public void write(Writer out) throws Exception {
            for (int i = 0; i < items.length; i += 2) {
                out.write(items[i] + ":" + items[i + 1]);
            }
        }

    }

}
