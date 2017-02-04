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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.locationtech.geogig.repository.AbstractGeoGigOp.CommandListener;

public class AbstractGeoGigOpTest {

    @Test
    public void testCall() {
        AbstractGeoGigOp<String> testOp = new AbstractGeoGigOp<String>() {
            @Override
            protected String _call() {
                return "myValue";
            }
            
        };
        assertEquals("myValue", testOp.call());
    }

    @Test
    public void testCommandListeners() {
        AtomicBoolean preCalled1 = new AtomicBoolean(false);
        AtomicBoolean postCalled1 = new AtomicBoolean(false);
        AtomicBoolean preCalled2 = new AtomicBoolean(false);
        AtomicBoolean postCalled2 = new AtomicBoolean(false);
        CommandListener listener1 = new CommandListener() {

            @Override
            public void preCall(AbstractGeoGigOp<?> command) {
                preCalled1.set(true);
            }

            @Override
            public void postCall(AbstractGeoGigOp<?> command, Object result,
                    RuntimeException exception) {
                assertEquals(result, "myValue");
                postCalled1.set(true);
            }
        };
        CommandListener listener2 = new CommandListener() {

            @Override
            public void preCall(AbstractGeoGigOp<?> command) {
                preCalled2.set(true);
            }

            @Override
            public void postCall(AbstractGeoGigOp<?> command, Object result,
                    RuntimeException exception) {
                assertEquals("myValue", result);
                postCalled2.set(true);
            }
        };
        AbstractGeoGigOp<String> testOp = new AbstractGeoGigOp<String>() {
            @Override
            protected String _call() {
                return "myValue";
            }
        };
        testOp.addListener(listener1);
        testOp.addListener(listener2);
        testOp.call();
        assertTrue(preCalled1.get());
        assertTrue(postCalled1.get());
        assertTrue(preCalled2.get());
        assertTrue(postCalled2.get());

        RuntimeException commandException = new RuntimeException("command exception");
        AtomicBoolean preCalled3 = new AtomicBoolean(false);
        AtomicBoolean postCalled3 = new AtomicBoolean(false);
        CommandListener listener3 = new CommandListener() {

            @Override
            public void preCall(AbstractGeoGigOp<?> command) {
                preCalled3.set(true);
            }

            @Override
            public void postCall(AbstractGeoGigOp<?> command, Object result,
                    RuntimeException exception) {
                assertEquals(null, result);
                assertEquals(commandException, exception);
                postCalled3.set(true);
            }
        };

        AbstractGeoGigOp<String> testOp2 = new AbstractGeoGigOp<String>() {
            @Override
            protected String _call() {
                throw commandException;
            }
        };
        testOp2.addListener(listener3);
        try {
            testOp2.call();
            fail();
        } catch (RuntimeException e) {
            // expected
        }

        assertTrue(preCalled3.get());
        assertTrue(postCalled3.get());
    }

    @Test
    public void testProgressListener() {
        AbstractGeoGigOp<String> testOp = new AbstractGeoGigOp<String>() {
            @Override
            protected String _call() {
                return "myValue";
            }
        };
        DefaultProgressListener listener = new DefaultProgressListener();
        listener.setMaxProgress(1000);
        listener.setDescription("command progress");

        testOp.setProgressListener(null);
        assertTrue(testOp.getProgressListener() instanceof ProgressListener);

        testOp.setProgressListener(listener);
        assertEquals(listener, testOp.getProgressListener());

        ProgressListener subProgress = testOp.subProgress(100);
        subProgress.setMaxProgress(100);
        subProgress.setProgress(50);
        assertEquals(50, (int) listener.getProgress());

        subProgress.complete();
        assertEquals(100, (int) listener.getProgress());
    }

    @Test
    public void testContext() {
        Context context = mock(Context.class);
        AbstractGeoGigOp<String> testOp = new AbstractGeoGigOp<String>() {
            @Override
            protected String _call() {
                return "myValue";
            }
        };
        testOp.setContext(context);
        assertEquals(context, testOp.context());

        testOp.command(AbstractGeoGigOp.class);
        verify(context).command(AbstractGeoGigOp.class);

        testOp.workingTree();
        verify(context).workingTree();

        testOp.stagingArea();
        verify(context).stagingArea();

        testOp.refDatabase();
        verify(context).refDatabase();

        testOp.platform();
        verify(context).platform();

        testOp.objectDatabase();
        verify(context).objectDatabase();

        testOp.conflictsDatabase();
        verify(context).conflictsDatabase();

        testOp.configDatabase();
        verify(context).configDatabase();

        testOp.graphDatabase();
        verify(context).graphDatabase();

        testOp.repository();
        verify(context).repository();
    }
}
