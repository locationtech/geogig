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

import org.locationtech.geogig.plumbing.diff.Patch;

/**
 * This exception indicate that a given patch is outdated and does not correspond to the current
 * state of the working tree, so it cannot be applied.
 * 
 */
public class CannotApplyPatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A patch with the conflicting changes of the patch that caused the exception
     * 
     */
    private Patch patch;

    public CannotApplyPatchException(Patch patch) {
        super("Error: Patch cannot be applied\n\nConflicting entries:\n\n" + patch.toString());
        this.patch = patch;
    }

    public Patch getPatch() {
        return patch;
    }

}
