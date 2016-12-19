/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

/**
 * Exception that indicates that a rebase operation cannot be finished due to merge conflicts
 */
public class RebaseConflictsException extends ConflictsException {

    private static final long serialVersionUID = 1L;

    public RebaseConflictsException(String msg) {
        super(msg);
    }

}
