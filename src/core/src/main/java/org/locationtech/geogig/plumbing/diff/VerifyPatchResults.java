/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

/**
 * A class to contains the results of a verify patch operation. It contains two patches, one with
 * the changes that can be applied on the current working tree, and another one with the changes
 * that cannot be applied
 * 
 */
public class VerifyPatchResults {

    private Patch toApply;

    private Patch toReject;

    public Patch getToApply() {
        return toApply;
    }

    /**
     * Returns the patch with the changes to reject
     * 
     * @return
     */
    public Patch getToReject() {
        return toReject;
    }

    /**
     * Returns the patch with the changes to apply
     * 
     * @return
     */
    public VerifyPatchResults(Patch toApply, Patch toReject) {
        this.toApply = toApply;
        this.toReject = toReject;

    }
}
