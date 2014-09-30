/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

/**
 * A default progress listener to be used for extending it.
 * 
 * It is also a functional listener that does not show progress, so it can actually be used as a
 * silet progress listener
 * 
 */
public class DefaultProgressListener implements ProgressListener {

    public static final ProgressListener NULL = new DefaultProgressListener();

    /**
     * Description of the current action.
     */
    protected String description;

    /**
     * Current progress value
     */
    protected float progress;

    /**
     * {@code true} if the action is canceled.
     */
    protected boolean canceled = false;

    /**
     * {@code true} if the action has already been completed.
     */
    protected boolean completed = false;

    /**
     * The maximum expected value of the progress.
     * 
     * By default, it has a value of 100, so it assumes that the progress value is a percent value
     */
    protected float maxProgress = 100f;

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public void started() {
        // do nothing
    }

    @Override
    public void setProgress(float progress) {
        this.progress = progress;
    }

    @Override
    public float getProgress() {
        return progress;
    }

    @Override
    public void complete() {
        this.completed = true;
    }

    @Override
    public boolean isCompleted() {
        return this.completed;
    }

    @Override
    public void dispose() {
        // do nothing
    }

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setMaxProgress(float maxProgress) {
        this.maxProgress = maxProgress;

    }

    @Override
    public float getMaxProgress() {
        return this.maxProgress;
    }
}
