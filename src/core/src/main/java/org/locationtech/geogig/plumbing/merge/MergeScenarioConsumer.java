/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;

/**
 * Consumes conflicted, unconflicted, and merged features from classes that iterate over them. This
 * default implementation does not do anything with the features.
 */
public class MergeScenarioConsumer {

    private AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Called when a conflict is found while processing a merge scenario.
     * 
     * @param conflict the conflict that was found
     */
    public void conflicted(Conflict conflict) {
    }

    /**
     * Called when an unconflicting feature is found while processing a merge scenario.
     * 
     * @param diff the unconflicting change
     */
    public void unconflicted(DiffEntry diff) {
    }

    /**
     * Called when a feature is found that was modified in both branches, but can be safely merged.
     * 
     * @param featureInfo the merged feature
     */
    public void merged(FeatureInfo featureInfo) {
    }

    /**
     * Called when the merge scenario is finished processing.
     */
    public void finished() {
    }

    /**
     * @return whether or not the iteration should be cancelled
     */
    public final boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Cancels the iteration over features.
     */
    public final void cancel() {
        cancelled.set(true);
        cancelled();
    }

    protected void cancelled() {

    }

}
