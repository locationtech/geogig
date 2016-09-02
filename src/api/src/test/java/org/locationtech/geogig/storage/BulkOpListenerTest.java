/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.BulkOpListener.ForwardingListener;

public class BulkOpListenerTest {

    @Test
    public void testCountingListener() {
        CountingListener listener = BulkOpListener.newCountingListener();
        assertEquals(0, listener.deleted());
        assertEquals(0, listener.inserted());
        assertEquals(0, listener.found());
        assertEquals(0, listener.notFound());

        listener.found(ObjectId.NULL, 0);
        listener.found(ObjectId.NULL, 0);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);

        assertEquals(2, listener.found());
        assertEquals(3, listener.deleted());
        assertEquals(4, listener.inserted());
        assertEquals(5, listener.notFound());
    }

    @Test
    public void testForwardingListener() {
        CountingListener countingListener = BulkOpListener.newCountingListener();
        ForwardingListener listener = new ForwardingListener(countingListener);
        assertEquals(0, countingListener.deleted());
        assertEquals(0, countingListener.inserted());
        assertEquals(0, countingListener.found());
        assertEquals(0, countingListener.notFound());

        listener.found(ObjectId.NULL, 0);
        listener.found(ObjectId.NULL, 0);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);

        assertEquals(2, countingListener.found());
        assertEquals(3, countingListener.deleted());
        assertEquals(4, countingListener.inserted());
        assertEquals(5, countingListener.notFound());
    }

    @Test
    public void testCompositeListener() {
        CountingListener countingListener1 = BulkOpListener.newCountingListener();
        CountingListener countingListener2 = BulkOpListener.newCountingListener();
        BulkOpListener listener = BulkOpListener.composite(countingListener1, countingListener2);

        assertEquals(0, countingListener1.deleted());
        assertEquals(0, countingListener1.inserted());
        assertEquals(0, countingListener1.found());
        assertEquals(0, countingListener1.notFound());
        assertEquals(0, countingListener2.deleted());
        assertEquals(0, countingListener2.inserted());
        assertEquals(0, countingListener2.found());
        assertEquals(0, countingListener2.notFound());

        listener.found(ObjectId.NULL, 0);
        listener.found(ObjectId.NULL, 0);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.deleted(ObjectId.NULL);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.inserted(ObjectId.NULL, 0);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);
        listener.notFound(ObjectId.NULL);

        assertEquals(2, countingListener1.found());
        assertEquals(3, countingListener1.deleted());
        assertEquals(4, countingListener1.inserted());
        assertEquals(5, countingListener1.notFound());
        assertEquals(2, countingListener2.found());
        assertEquals(3, countingListener2.deleted());
        assertEquals(4, countingListener2.inserted());
        assertEquals(5, countingListener2.notFound());
    }

    @Test
    public void testCompositeListenerWithNoOp() {
        CountingListener countingListener = BulkOpListener.newCountingListener();
        BulkOpListener listener = BulkOpListener.composite(countingListener,
                BulkOpListener.NOOP_LISTENER);
        assertEquals(countingListener, listener);
        listener = BulkOpListener.composite(BulkOpListener.NOOP_LISTENER, countingListener);
        assertEquals(countingListener, listener);

        listener = BulkOpListener.composite(BulkOpListener.NOOP_LISTENER,
                BulkOpListener.NOOP_LISTENER);
        assertEquals(BulkOpListener.NOOP_LISTENER, listener);

        listener.found(ObjectId.NULL, 0);
        listener.deleted(ObjectId.NULL);
        listener.inserted(ObjectId.NULL, 0);
        listener.notFound(ObjectId.NULL);
    }

}
