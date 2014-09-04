/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import org.locationtech.geogig.api.ObjectId;

/**
 * Exception that indicates that a merge operation cannot be finished due to merge conflicts
 */
public class MergeConflictsException extends ConflictsException {

    private static final long serialVersionUID = 1L;

    private ObjectId ours = null;

    private ObjectId theirs = null;

    public MergeConflictsException(String msg) {
        super(msg);
    }

    public MergeConflictsException(String msg, ObjectId ours, ObjectId theirs) {
        super(msg);
        this.ours = ours;
        this.theirs = theirs;
    }

    public ObjectId getOurs() {
        return this.ours;
    }

    public ObjectId getTheirs() {
        return this.theirs;
    }

}
