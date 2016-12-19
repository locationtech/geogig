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
 * Indicates there are no staged changes to commit as the result of the execution of a
 * {@link CommitOp}
 * 
 */
public class NothingToCommitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code NothingToCommitException} with the given message.
     * 
     * @param msg the message for the exception
     */
    public NothingToCommitException(String msg) {
        super(msg);
    }
}
