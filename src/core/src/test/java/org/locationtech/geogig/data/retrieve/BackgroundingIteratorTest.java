/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;
import org.locationtech.geogig.data.retrieve.BackgroundingIterator;

import static org.mockito.Mockito.*;

import com.vividsolutions.jts.util.Assert;

public class BackgroundingIteratorTest {

    @Test
    public void test0Elements() {
        Iterator it = (new ArrayList()).iterator();
        BackgroundingIterator bit = new BackgroundingIterator(it, 100);
        Assert.isTrue(!bit.hasNext());
        Assert.isTrue(!bit.hasNext());
    }

    @Test
    public void test1Elements() {
        Iterator it = (Arrays.asList(1)).iterator();
        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);
        Assert.isTrue(bit.hasNext());
        Assert.isTrue(bit.hasNext()); // can call multiple times
        Assert.isTrue(bit.hasNext()); // can call multiple times

        Integer i = bit.next();
        Assert.isTrue(i.intValue() == 1);
        Assert.isTrue(!bit.hasNext());
        Assert.isTrue(!bit.hasNext());
    }

    @Test
    public void test2Elements() {
        Iterator it = (Arrays.asList(1, 2)).iterator();
        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);
        Assert.isTrue(bit.hasNext());
        Assert.isTrue(bit.hasNext()); // can call multiple times
        Assert.isTrue(bit.hasNext()); // can call multiple times

        Integer i = bit.next();
        Assert.isTrue(i.intValue() == 1);
        Assert.isTrue(bit.hasNext());
        Assert.isTrue(bit.hasNext()); // can call multiple times
        Assert.isTrue(bit.hasNext()); // can call multiple times

        i = bit.next();
        Assert.isTrue(i.intValue() == 2);
        Assert.isTrue(!bit.hasNext());
        Assert.isTrue(!bit.hasNext()); // can call multiple times
    }

    @Test(expected = NoSuchElementException.class)
    public void testOffEdgeThrowsError() {
        Iterator it = (Arrays.asList(1, 2)).iterator();
        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);

        Integer i = bit.next();
        i = bit.next();
        i = bit.next(); // fails
    }

    @Test
    public void testOkNotToCallHasNext() {
        Iterator it = (Arrays.asList(1, 2)).iterator();
        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);

        Integer i = bit.next();
        Assert.isTrue(i.intValue() == 1);
        i = bit.next();
        Assert.isTrue(i.intValue() == 2);
    }

    @Test
    public void testIteratorHasErrorFirstElement_next() {
        Exception except = new RuntimeException();
        Iterator it = mock(Iterator.class);
        when(it.hasNext()).thenReturn(Boolean.TRUE);
        when(it.next()).thenThrow(except);

        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);
        try {
            Integer i = bit.next(); // should throw
        } catch (Exception e) {
            Assert.isTrue(e.getCause() == except);
            return; // done
        }
        Assert.shouldNeverReachHere();
    }

    @Test
    public void testIteratorHasErrorFirstElement_hasNext() {
        Exception except = new RuntimeException();
        Iterator it = mock(Iterator.class);
        when(it.hasNext()).thenReturn(Boolean.TRUE);
        when(it.next()).thenThrow(except);

        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);
        try {
            bit.hasNext();
        } catch (Exception e) {
            Assert.isTrue(e.getCause() == except);
            return; // done
        }
        Assert.shouldNeverReachHere();
    }

    @Test
    public void testIteratorHasError2ndElement_next() {
        Exception except = new RuntimeException();
        Iterator it = mock(Iterator.class);
        when(it.hasNext()).thenReturn(Boolean.TRUE);
        when(it.next()).thenReturn(1).thenThrow(except);

        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);

        Integer i = bit.next();
        Assert.isTrue(i.intValue() == 1);
        try {
            i = bit.next(); // should throw
        } catch (Exception e) {
            Assert.isTrue(e.getCause() == except);
            return; // done
        }
        Assert.shouldNeverReachHere();
    }

    @Test
    public void testIteratorHasError2ndElement_hasNext() {
        Exception except = new RuntimeException();
        Iterator it = mock(Iterator.class);
        when(it.hasNext()).thenReturn(Boolean.TRUE);
        when(it.next()).thenReturn(1).thenThrow(except);

        BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it, 100);

        Integer i = bit.next();
        Assert.isTrue(i.intValue() == 1);
        try {
            bit.hasNext(); // should throw
        } catch (Exception e) {
            Assert.isTrue(e.getCause() == except);
            return; // done
        }
        Assert.shouldNeverReachHere();
    }

    // @Test
    // public void testThreadCleanup() throws InterruptedException{
    // for (int t=0;t<100;t++) {
    // Iterator it = (Arrays.asList(1,2,3,4,5,6,7,8,9,10) ).iterator();
    // BackgroundingIterator<Integer> bit = new BackgroundingIterator<Integer>(it,5);
    // }
    //
    // System.runFinalization();
    // System.runFinalizersOnExit(true);;
    // System.gc();
    // Thread.sleep(1000);
    // System.runFinalization();
    // System.gc();
    // Thread.sleep(1000);
    // System.gc();
    // Thread.sleep(1000);
    // System.gc();
    // Thread.sleep(1000);
    // Thread.sleep(5000);
    // Thread.sleep(5000);
    // for (int i=0;i<1000000;i++){
    // String s = new
    // String("lakjfdlakjfl;ajdf;laskjdflajfdl;ajfl;ajlfjal;frjoweiruqwoeifjagvhjpqobna;eobgpqogbnewqpovjbjnnqeovbqeovboeevbqeo0vrb");
    // }
    // }
}
