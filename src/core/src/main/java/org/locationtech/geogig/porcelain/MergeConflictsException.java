/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;

/**
 * Exception that indicates that a merge operation cannot be finished due to merge conflicts
 */
public class MergeConflictsException extends ConflictsException {

    private static final long serialVersionUID = 1L;

    private ObjectId ours = null;

    private ObjectId theirs = null;

    private MergeScenarioReport mergeScenario;

    public MergeConflictsException(String msg) {
        super(msg);
    }

    public MergeConflictsException(String msg, ObjectId ours, ObjectId theirs,
            MergeScenarioReport mergeScenario) {
        super(msg);
        this.ours = ours;
        this.theirs = theirs;
        this.mergeScenario = mergeScenario;
    }

    public ObjectId getOurs() {
        return this.ours;
    }

    public ObjectId getTheirs() {
        return this.theirs;
    }

    public MergeScenarioReport getReport() {
        return mergeScenario;
    }
}
