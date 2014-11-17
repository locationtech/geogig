/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.metrics;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class CallStackTest extends Assert {

    @After
    public void tearDown() {
        CallStack.CALL_STACK.remove();
    }

    @Test
    public void testRoot() {
        CallStack root = CallStack.push("op1", 1, 1);
        assertNotNull(root);
        assertTrue(root.isRoot());
        assertEquals("op1", root.getName());
        assertEquals(-1L, root.getNanoTime());
        assertFalse(root.isSuccess());
        assertFalse(root.isFinished());

        CallStack pop = CallStack.pop(1, true);
        assertSame(root, pop);
        assertTrue(pop.isFinished());
        assertEquals(1L, pop.getNanoTime());
        assertTrue(pop.isSuccess());

        assertNull(CallStack.CALL_STACK.get());
    }

    @Test
    public void testMultipleRootsNoChildren() {
        CallStack root1 = CallStack.push("op1", 1, 1);
        assertSame(root1, CallStack.pop(1, true));
        assertNull(CallStack.CALL_STACK.get());

        CallStack root2 = CallStack.push("op2", 1, 1);
        assertSame(root2, CallStack.pop(2, true));
        assertNull(CallStack.CALL_STACK.get());

        CallStack root3 = CallStack.push("op3", 1, 1);
        assertSame(root3, CallStack.pop(3, false));
        assertNull(CallStack.CALL_STACK.get());
    }

    @Test
    public void testChildrenOneLevel() {
        CallStack root = CallStack.push("op1", 1, 1);

        CallStack c1 = CallStack.push("child1", 1, 1);
        assertSame(c1, CallStack.pop(1, true));

        CallStack c2 = CallStack.push("child2", 1, 1);
        assertSame(c2, CallStack.pop(1, true));

        CallStack c3 = CallStack.push("child3", 1, 1);
        assertSame(c3, CallStack.pop(1, true));

        assertSame(root, CallStack.pop(10, true));
        assertNull(CallStack.CALL_STACK.get());
    }

    @Test
    public void testNested() {
        CallStack root = CallStack.push("root", 1, 1);

        CallStack c1 = CallStack.push("child1", 1, 1);
        CallStack c11 = CallStack.push("child11", 1, 1);
        CallStack c111 = CallStack.push("child111", 1, 1);
        assertSame(c111, CallStack.pop(1, true));
        CallStack c112 = CallStack.push("child112", 1, 1);
        assertSame(c112, CallStack.pop(1, true));
        assertSame(c11, CallStack.pop(1, true));
        assertSame(c1, CallStack.pop(1, true));
        assertSame(root, CallStack.pop(Long.MAX_VALUE, true));
        System.out.println(root.toString(TimeUnit.MILLISECONDS));
        assertNull(CallStack.CALL_STACK.get());
    }
}
