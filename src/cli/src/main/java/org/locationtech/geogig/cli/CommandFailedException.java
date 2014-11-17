/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli;

/**
 * An exception to indicate that a GeoGig CLI command has failed and the CLI should exit with
 * non-zero code.
 * <p>
 * This exception is to be thrown when the execution of the core command the CLI command calls
 * actually failed, and shall contain as much information of the cause of the execution failure as
 * possible.
 * <p>
 * For other kinds of exceptions a CLI command may throw see {@link CLICommand#run(GeogigCLI)
 * CLICommand.run()}
 * <p>
 * The exception message for instances of this class are meant to be reported on the console. The
 * {@link #getCause() original} exception is meant to be logged if logging is enabled, so its
 * strongly encouraged to include it whenever possible.
 */
public class CommandFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Empty constructor, use it only to signal abnormal command termination but there's nothing to
     * report to the console or log.
     */
    public CommandFailedException() {
        super();
    }

    public CommandFailedException(String message) {
        super(message);
    }

    public CommandFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandFailedException(Throwable cause) {
        super(cause);
    }

}
