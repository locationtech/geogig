package org.locationtech.geogig.remotes.pack;

import java.util.concurrent.BlockingQueue;

import com.google.common.collect.AbstractIterator;

class BlockingIterator<T> extends AbstractIterator<T> {

    private final BlockingQueue<T> queue;

    private final T terminalToken;

    public BlockingIterator(BlockingQueue<T> queue, T terminalToken) {
        this.queue = queue;
        this.terminalToken = terminalToken;
    }

    @Override
    protected T computeNext() {
        T object;
        try {
            object = queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        if (terminalToken.equals(object)) {
            return endOfData();
        }
        return object;
    }
}