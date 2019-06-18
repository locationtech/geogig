/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.function.Function;

import com.google.common.util.concurrent.AtomicDouble;

/**
 * A default progress listener to be used for extending it.
 * 
 * It is also a functional listener that does not show progress, so it can actually be used as a
 * silent progress listener
 * 
 * @since 1.0
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
    private AtomicDouble progress = new AtomicDouble();

    /**
     * {@code true} if the action is canceled.
     */
    protected volatile boolean canceled = false;

    /**
     * {@code true} if the action has already been completed.
     */
    protected volatile boolean completed = false;

    /**
     * The maximum expected value of the progress.
     * 
     * By default, it has a value of 100, so it assumes that the progress value is a percent value
     */
    protected float maxProgress = 100f;

    private Function<ProgressListener, String> progressIndicator = DEFAULT_PROGRES_INDICATOR;

    /**
     * @return the description of the current task
     */
    public @Override String getDescription() {
        return description;
    }

    public @Override void setDescription(String format, Object... args) {
        this.description = String.format(format, args);
    }

    /**
     * Called when a task begins tracking progress.
     */
    public @Override void started() {
        completed = false;
        progress.set(0);
    }

    /**
     * Update the progress of the task.
     * 
     * @param progress the new progress
     */
    public @Override void setProgress(float progress) {
        this.progress.set(progress);
    }

    public @Override void incrementBy(float amount) {
        this.progress.addAndGet(amount);
    }

    /**
     * @return the current progress of the task
     */
    public @Override float getProgress() {
        return progress.floatValue();
    }

    /**
     * Called when the task is completed. This will update the progress to the maximum progress
     * value.
     */
    public @Override void complete() {
        setProgress(getMaxProgress());
        this.completed = true;
    }

    /**
     * @return {@code true} if the task is complete
     */
    public @Override boolean isCompleted() {
        return this.completed;
    }

    /**
     * Called when the progress listener is no longer needed.
     */
    public @Override void dispose() {
        // do nothing
    }

    /**
     * Called to indicate that the current task has been cancelled.
     */
    public @Override void cancel() {
        this.canceled = true;
    }

    /**
     * @return {@code true} if the task was cancelled
     */
    public @Override boolean isCanceled() {
        return canceled;
    }

    /**
     * Sets the maximum value for the progress listener.
     * 
     * @param maxProgress the new maximum value
     */
    public @Override void setMaxProgress(float maxProgress) {
        this.maxProgress = maxProgress;

    }

    /**
     * @return the maximum value of the progress listener
     */
    public @Override float getMaxProgress() {
        return this.maxProgress;
    }

    public @Override void setProgressIndicator(
            Function<ProgressListener, String> progressIndicator) {
        this.progressIndicator = progressIndicator == null ? DEFAULT_PROGRES_INDICATOR
                : progressIndicator;
    }

    public @Override Function<ProgressListener, String> progressIndicator() {
        return progressIndicator;
    }
}
