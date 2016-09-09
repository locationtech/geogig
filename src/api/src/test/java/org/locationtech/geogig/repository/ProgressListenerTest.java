/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProgressListenerTest {

    @Test
    public void testDefaultListener() {
        ProgressListener progress = new DefaultProgressListener();
        progress.setDescription("progress description");
        progress.setMaxProgress(500);
        progress.started();
        assertEquals("progress description", progress.getDescription());
        assertEquals(0, (int) progress.getProgress());
        progress.setProgress(250);
        assertEquals(250, (int) progress.getProgress());
        progress.complete();
        assertEquals(500, (int) progress.getProgress());
        assertTrue(progress.isCompleted());
        progress.dispose();
    }

    @Test
    public void testCancelDefaultListener() {
        ProgressListener progress = new DefaultProgressListener();
        progress.setDescription("progress description");
        progress.setMaxProgress(500);
        progress.started();
        assertEquals("progress description", progress.getDescription());
        assertEquals(0, (int) progress.getProgress());
        progress.setProgress(250);
        assertEquals(250, (int) progress.getProgress());
        progress.cancel();
        assertTrue(progress.isCanceled());
        progress.dispose();
    }

    @Test
    public void testSubProgress() {
        ProgressListener progress = new DefaultProgressListener();
        progress.setDescription("progress description");
        progress.setMaxProgress(100);
        progress.started();
        SubProgressListener subProgress = new SubProgressListener(progress, 20);
        assertEquals("progress description", progress.getDescription());
        assertEquals("progress description", subProgress.getDescription());
        subProgress.setDescription("sub progress description");
        assertEquals("sub progress description", progress.getDescription());
        assertEquals("sub progress description", subProgress.getDescription());
        subProgress.setMaxProgress(1000);
        subProgress.started();
        assertEquals(0, (int) progress.getProgress());
        assertEquals(0, (int) subProgress.getProgress());
        subProgress.setProgress(100);
        assertEquals(100, (int) subProgress.getProgress());
        assertEquals(2, (int) progress.getProgress());
        subProgress.setProgress(500);
        assertEquals(500, (int) subProgress.getProgress());
        assertEquals(10, (int) progress.getProgress());
        subProgress.complete();
        assertEquals(1000, (int) subProgress.getProgress());
        assertEquals(20, (int) progress.getProgress());
        assertTrue(subProgress.isCompleted());
        subProgress.dispose();
        progress.dispose();
    }

    @Test
    public void testCancelSubProgress() {
        ProgressListener progress = new DefaultProgressListener();
        progress.setMaxProgress(100);
        SubProgressListener subProgress = new SubProgressListener(progress, 20);
        subProgress.setProgress(10);
        assertEquals(10, (int) subProgress.getProgress());
        assertEquals(2, (int) progress.getProgress());
        subProgress.cancel();
        assertTrue(subProgress.isCanceled());
        assertTrue(progress.isCanceled());
        subProgress.dispose();
        progress.dispose();
    }

    @Test
    public void testNegativeSubProgress() {
        ProgressListener progress = new DefaultProgressListener();
        progress.setMaxProgress(100);
        SubProgressListener subProgress = new SubProgressListener(progress, -20);
        subProgress.setProgress(50);
        assertEquals(50, (int) subProgress.getProgress());
        assertEquals(0, (int) progress.getProgress());
        subProgress.complete();
        assertEquals(100, (int) subProgress.getProgress());
        assertEquals(0, (int) progress.getProgress());
        subProgress.dispose();
        progress.dispose();
    }
}
