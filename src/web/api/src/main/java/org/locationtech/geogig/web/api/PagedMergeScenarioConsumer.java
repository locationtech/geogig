/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.merge.MergeScenarioConsumer;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.web.api.commands.ReportMergeScenario;

/**
 * Implementation of {@link MergeScenarioConsumer} that will store a page of results.
 */
public class PagedMergeScenarioConsumer extends MergeScenarioConsumer {

    private int skip;

    private List<Conflict> conflicted = new LinkedList<Conflict>();

    private List<DiffEntry> unconflicted = new LinkedList<DiffEntry>();

    private List<FeatureInfo> merged = new LinkedList<FeatureInfo>();

    private boolean finished = false;

    private int featureCount = 0;

    private int elementsPerPage;

    /**
     * Constructs a new {@link PagedMergeScenarioConsumer} with the provided page number.
     * 
     * @param page the page of results to return
     */
    public PagedMergeScenarioConsumer(int page) {
        this(page, ReportMergeScenario.DEFAULT_MERGE_SCENARIO_PAGE_SIZE);
    }

    /**
     * Constructs a new {@link PagedMergeScenarioConsumer} with the provided page number and
     * elements per page.
     * 
     * @param page the page of results to return
     * @param elementsPerPage the number of features per page
     */
    public PagedMergeScenarioConsumer(int page, int elementsPerPage) {
        this.skip = page * elementsPerPage;
        this.elementsPerPage = elementsPerPage;
    }

    /**
     * Keeps track of which features should be skipped, and which should be retained.
     * 
     * @return true if the feature should be kept
     */
    private boolean shouldKeep() {
        if (skip <= 0) {
            if (++featureCount == elementsPerPage) {
                this.cancel();
            }
            return true;
        } else {
            skip--;
        }
        return false;
    }

    /**
     * Called when a conflict is found while processing a merge scenario.
     * 
     * @param conflict the conflict that was found
     */
    @Override
    public void conflicted(Conflict conflict) {
        if (shouldKeep()) {
            conflicted.add(conflict);
        }
    }

    /**
     * Called when an unconflicting feature is found while processing a merge scenario.
     * 
     * @param diff the unconflicting change
     */
    @Override
    public void unconflicted(DiffEntry diff) {
        if (RevObject.TYPE.FEATURE.equals(diff.newObjectType()) && shouldKeep()) {
            unconflicted.add(diff);
        }
    }

    /**
     * Called when a feature is found that was modified in both branches, but can be safely merged.
     * 
     * @param featureInfo the merged feature
     */
    @Override
    public void merged(FeatureInfo featureInfo) {
        if (shouldKeep()) {
            merged.add(featureInfo);
        }
    }

    /**
     * Called when the merge scenario is finished processing.
     */
    @Override
    public void finished() {
        finished = true;
    }

    /**
     * @return whether or not the merge scenario finished processing
     */
    public boolean didFinish() {
        return finished;
    }

    /**
     * @return the conflicted features for the page
     */
    public Iterator<Conflict> getConflicted() {
        return conflicted.iterator();
    }

    /**
     * @return the unconflicted features for the page
     */
    public Iterator<DiffEntry> getUnconflicted() {
        return unconflicted.iterator();
    }

    /**
     * @return the merged features for the page
     */
    public Iterator<FeatureInfo> getMerged() {
        return merged.iterator();
    }
}
