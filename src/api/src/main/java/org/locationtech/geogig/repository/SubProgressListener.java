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

/**
 * A progress listener that can be used to track the progress of a subtask of a parent
 * {@link ProgressListener}. This listener's full range will be mapped to a portion of the progress
 * of the parent listener.
 * 
 * @since 1.0
 */
public class SubProgressListener extends DefaultProgressListener {

    /** Initial starting value */
    private float start;

    /** Amount of work we have been asked to perform */
    private float amount;

    private ProgressListener parentProgressListener;

    /**
     * Create a sub progress monitor, used to delegate work to a separate process.
     * 
     * @param progress parent progress to notify as we get work done
     * @param amount amount of progress represented
     */
    public SubProgressListener(ProgressListener progress, float amount) {
        super();
        parentProgressListener = progress;
        this.start = progress.getProgress();
        this.amount = (amount > 0.0f) ? amount : 0.0f;
    }

    /**
     * Called when a task begins tracking progress.
     */
    @Override
    public void started() {
        setProgress(0.f);
    }

    /**
     * Called when the task is completed. This will update the progress to the maximum progress
     * value. Also updates the parent progress listener accordingly.
     */
    @Override
    public void complete() {
        parentProgressListener.setProgress(start + amount);
        super.complete();
    }

    /**
     * @return the current progress of the task
     */
    @Override
    public float getProgress() {
        return super.getProgress();
    }

    /**
     * Update the progress of the task. Also updates the parent progress listener accordingly.
     * 
     * @param progress the new progress
     */
    @Override
    public void setProgress(float progress) {
        super.setProgress(progress);
        float percent = progress / getMaxProgress();
        parentProgressListener.setProgress(start + (amount * percent));
    }

    @Override
    public void setDescription(String format, Object... args) {
        parentProgressListener.setDescription(format, args);
    }

    /**
     * @return the description of the current task
     */
    @Override
    public String getDescription() {
        return parentProgressListener.getDescription();
    }

    /**
     * Sets the maximum value for the progress listener.
     * 
     * @param maxProgress the new maximum value
     */
    @Override
    public void setMaxProgress(float maxProgress) {
        super.setMaxProgress(maxProgress);
    }

    /**
     * @return the maximum value of the progress listener
     */
    @Override
    public float getMaxProgress() {
        return super.getMaxProgress();
    }

    /**
     * @return {@code true} if the task is complete
     */
    @Override
    public boolean isCompleted() {
        return super.isCompleted();
    }

    /**
     * Called when the progress listener is no longer needed.
     */
    @Override
    public void dispose() {
        super.dispose();
    }

    /**
     * @return {@code true} if the task was cancelled
     */
    @Override
    public boolean isCanceled() {
        return parentProgressListener.isCanceled();
    }

    /**
     * Called to indicate that the current task has been cancelled.
     */
    @Override
    public void cancel() {
        parentProgressListener.cancel();
    }
}