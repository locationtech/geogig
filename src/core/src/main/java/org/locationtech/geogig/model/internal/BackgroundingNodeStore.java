/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 */
package org.locationtech.geogig.model.internal;

import com.google.common.base.Throwables;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * queues up nodes to be saved out to disk and uses a background thread to save them.
 * <p>
 * The optimal usage pattern is WRITE, then READ.
 * However, it does support WRITE-READ-WRITE-READ
 * If you do this, it will write all the outstanding items to the underlying
 * Store before allowing the reads to occur.
 * <p>
 * Do not run this class with multi-threaded (untested).
 * <p>
 * If an error occurs, the user will be warned and the service will shutdown.
 */
public class BackgroundingNodeStore implements NodeStore {

    public enum StateEnum {INIT, READING, WRITING}

    StateEnum state = StateEnum.INIT;

    NodeStore underlying;
    BlockingQueue<SaveNodeItem> queue = null; //items that need saving
    Consumer consumer; // thread that will do the saving
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    Future<Throwable> consumerFuture;


    //we put  this on the queue to indicate that the consumer should stop saving and shutdown
    public static SaveNodeItem POISON_PILL = new SaveNodeItem(null, null);


    public BackgroundingNodeStore(NodeStore underlying) {
        this(underlying, 100_000);
    }

    public BackgroundingNodeStore(NodeStore underlying, int nItems) {
        queue = new ArrayBlockingQueue<>(nItems);
        this.underlying = underlying;

        state = StateEnum.INIT;
    }


    //indicate we want to start writing.
    // 1. if needed, it will start up a new consumer
    // 2. if the consumer is dead (or in the process or dying), it will throw an exception
    void startWriting() throws Exception {
        if (state == StateEnum.WRITING) {
            //consumer should be up, but its down (or going down)
            if (consumerFuture.isDone() || consumer.consumerInProcessOfDying) {
                queue.clear();
                Throwables.propagate(consumerFuture.get());
            }
            return; //we're good
        }

        //start up a new consumer
        consumer = new Consumer(underlying, queue);
        consumerFuture = executorService.submit(consumer); //start saving
        state = StateEnum.WRITING;
    }


    //indicate we want to start reading
    // if we are in writing mode, we wait for all items to be saved
    //  to the underlying, then shut the consumer down.
    void startReading() {
        if (state == StateEnum.READING) {
            return; //we're good
        }
        if (state == StateEnum.INIT) {
            state = StateEnum.READING;
            return;
        }
        //stop writing, clean up then can start reading
        Throwable consumerError = null;
        try {
            queue.put(POISON_PILL);
            consumer = null;
            consumerError = consumerFuture.get(); //wait
            queue.clear();
            state = StateEnum.READING;

        } catch (Exception e) {
            Throwables.propagate(e);
        }
        if (consumerError != null)
            Throwables.propagate(consumerError);
    }


    //try really hard not to leak threads
    @Override
    protected void finalize() throws Throwable {
        if (!executorService.isShutdown())
            executorService.shutdownNow(); // the thread could be blocked
    }


    @Override
    public void close() {
        try {
            //most cases you're closing after reading, but make sure...
            startReading();
        } catch (Exception e) {
            Throwables.propagate(e);
        } finally {
            try {
                underlying.close();
            } finally {
                executorService.shutdownNow();
            }
        }
    }

    @Override
    public DAGNode get(NodeId nodeId) {
        startReading();
        return underlying.get(nodeId);
    }

    @Override
    public Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds) {
        startReading();
        return underlying.getAll(nodeIds);
    }

    @Override
    public void put(NodeId nodeId, DAGNode node) {
        try {
            startWriting();
            queue.put(new SaveNodeItem(nodeId, node));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //backed by put()
    @Override
    public void putAll(Map<NodeId, DAGNode> nodeMappings) {
        for (Map.Entry<NodeId, DAGNode> item : nodeMappings.entrySet()) {
            put(item.getKey(), item.getValue());
        }
    }

    //----------------------------------------------------------------------

    /**
     * hold info needed to save a node
     */
    public static class SaveNodeItem {
        NodeId nodeId;
        DAGNode node;

        public SaveNodeItem(NodeId nodeId, DAGNode node) {
            this.node = node;
            this.nodeId = nodeId;
        }
    }

    //----------------------------------------------------------------------


    /**
     * simple consumer that takes the queued items and writes them out
     * to the underlying store.
     * <p>
     * note - returns an error if an error occurred
     * <p>
     * NOTE: consumerInProcessOfDying will be set while this task is dying
     * do not add more items to the queue after this is set.
     * This deals with a threading issue where the producer
     * is creating new items after this has caught an error but
     * before it terminates.  Not usually an issue, but test cases
     * showed this can happen (only when the queue has a small max size).
     * The problem is that the producer could fill up the queue in this
     * very small amount of time and start blocking (making the process hang).
     */
    private class Consumer implements Callable<Throwable> {

        NodeStore underlying;
        BlockingQueue<SaveNodeItem> queue;

        public volatile boolean consumerInProcessOfDying = false;


        public Consumer(NodeStore underlying, BlockingQueue<SaveNodeItem> queue) {
            this.underlying = underlying;
            this.queue = queue;
        }

        @Override
        public Throwable call() {
            boolean keep_going = !Thread.interrupted();
            try {
                while (keep_going) {
                    SaveNodeItem item = queue.take();
                    if (item != POISON_PILL) {
                        underlying.put(item.nodeId, item.node);
                    }
                    keep_going = !Thread.interrupted() && item != POISON_PILL;
                }
            } catch (InterruptedException e) {
                //not much we can do - someone told us to stop
                consumerInProcessOfDying = true;
                queue.clear(); // make sure no one's blocking on us!
                return e;
            } catch (Throwable e) {
                //might be something like a disk full error
                consumerInProcessOfDying = true;
                queue.clear(); // make sure no one's blocking on us!
                return e;
            }
            return null; //normal exit
        }
    }
}
