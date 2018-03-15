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

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * This is a simple producer-consumer based Iterator that pulls using a background thread.
 * 
 * This uses a BlockingQueue to store a fixed number of items. As soon as you construct this
 * BackgroundingIterator, a thread will start that will start pulling items from the underlying
 * iterator.
 * 
 * The BlockingQueue will block the pulling thread when the queue gets full. hasNext()/next() will
 * block if there are no available items and wait while the pulling thread gets another item.
 * 
 * This is useful for iterators that access file/database items as they can be sped-up greatly by
 * using a pull thread.
 * 
 * A PoisonPill is put onto the queue to signify that there are no more items.
 *
 * If the underlyingIterator throws an exception, then;
 *    + if the queue has space, the PoisonPill (with error) is set.
 *    + If the queue is full, then it will BE EMPTIED to ensure the PoisonPill is immediately put
 *       on the queue so the producer can terminate.
 *
 * @param <T>
 */
public class BackgroundingIterator<T> implements Iterator<T>, Closeable,AutoCloseableIterator<T> {

    BlockingQueue<Object> queue;

    Producer producer;

    // last object retrieved from queue. with either be an item or PoisonPill
    // null = need to get one from queue
    Object currentObject = null;

    // when there are no more items, this will become true
    // mostly a quick way to ensure that next() isn't called after all the
    // items have been consumed (which will block forever)
    boolean done = false;

    ExecutorService executorService;

    Future future;

    public BackgroundingIterator(Iterator<T> underlyingIterator, int queueSize) {
        if (underlyingIterator == null) {
            throw new IllegalArgumentException("underlyingIterator is null");
        }
        queue = new ArrayBlockingQueue<Object>(queueSize);
        producer = new Producer(underlyingIterator, queue);
        ThreadFactory nameThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("backgrounding-iterator-%d").build();
        executorService = Executors.newSingleThreadExecutor(nameThreadFactory);
        future = executorService.submit(producer);
    }

    /**
     * possible case is the iterator is abandoned before all the elements are consumed. In that
     * case, we'll be leaking threads so we try to clean them up.
     */
    @Override
    protected void finalize() throws Throwable {
        executorService.shutdownNow(); // the thread could be blocked
        queue.clear();
    }

    @Override
    public void close() {
        queue.clear();
        executorService.shutdownNow(); // the thread could be blocked
        queue.clear();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean hasNext() {
        if (done) {
            return false;
        }
        if (currentObject == null) {
            try {
                currentObject = queue.take();
            } catch (InterruptedException e) {
                done = true;
                return false; // this shouldn't happen
            }
        }

        if (currentObject instanceof BackgroundingIterator.PoisonPill) {
            done = true;
            PoisonPill pill = (PoisonPill) currentObject;
            if (pill.hasError()) {
                throw new RuntimeException(pill.getError());
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T next() {
        if (done) {
            throw new NoSuchElementException();
        }
        if (currentObject == null) { // hasNext() not called
            if (!hasNext()) { // will populate currentObject
                throw new NoSuchElementException();
            }
        }
        T result = (T) currentObject;
        currentObject = null;
        return result;
    }

    /**
     * simple class that fills up the queue - this is expected to be run in a separate thread.
     * 
     * When there are no more items to be read, a PoisonPill is put on the queue to communicate this
     * to the consumer.
     * 
     * If an error occurs, the PoisonPill will contain the error.
     */
    class Producer implements Runnable {

        Iterator<T> underlyingIterator;

        BlockingQueue<Object> queue;

        public Producer(Iterator<T> underlyingIterator, BlockingQueue<Object> queue) {
            this.underlyingIterator = underlyingIterator;
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted() && underlyingIterator.hasNext()) {
                    queue.put(underlyingIterator.next());
                }
            } catch (Throwable th) {
                try {
                    if (queue.remainingCapacity() < 1) {
                        // this needs to go on the queue - make room.
                        // We are the only thread putting elements on the queue
                        queue.clear();
                    }

                    queue.put(new PoisonPill(th)); // signify end of elements
                    if (underlyingIterator instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) underlyingIterator).close();
                        } catch (Exception e) {
                          //do nothing - we tried our best
                        }
                    }
                    return;
                } catch (InterruptedException e) {

                }
            }
            try {
                queue.put(new PoisonPill()); // signify end of elements
            } catch (InterruptedException e) {

            }
            if (underlyingIterator instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) underlyingIterator).close();
                } catch (Exception e) {
                    //do nothing - we tried our best
                }
            }
        }

    }

    /**
     * Very simple class that allows the pulling thread to indicate there are no more items to put
     * on the queue.
     * 
     * PoisonPill is a common term and mean its a way to kill the upstream process (swallow the
     * PoisonPill).
     * 
     * This can contain an error if the producer encountered an error.
     */
    class PoisonPill {
        Throwable throwable = null;

        PoisonPill() {
        }

        PoisonPill(Throwable throwable) {
            this.throwable = throwable;
        }

        public boolean hasError() {
            return throwable != null;
        }

        public Throwable getError() {
            return throwable;
        }
    }


}
