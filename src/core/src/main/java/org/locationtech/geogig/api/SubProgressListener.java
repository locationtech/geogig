/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

public class SubProgressListener extends DefaultProgressListener {

    /** Initial starting value */
    float start;

    /** Amount of work we have been asked to perform */
    float amount;

    /** Scale between subprogress and delegate */
    float scale;

    ProgressListener parentProgressListener;

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
        float max = parentProgressListener.getMaxProgress();
        this.scale = this.amount / max;
    }

    @Override
    public void started() {
        super.progress = 0.0f;
    }

    @Override
    public void complete() {
        parentProgressListener.setProgress(start + amount);
        progress = getMaxProgress();
    }

    @Override
    public float getProgress() {
        return progress;
    }

    @Override
    public void setProgress(float progress) {
        this.progress = progress;
        parentProgressListener.setProgress(start + (scale * progress));
    }

    @Override
    public void setDescription(String description) {
        parentProgressListener.setDescription(description);
    }
}