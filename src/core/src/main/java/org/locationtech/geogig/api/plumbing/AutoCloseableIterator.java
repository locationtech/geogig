/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

/**
 * Interface for an iterator that can do some cleanup or other work when it is no longer needed. Can
 * be used in conjunction with a try-with-resources block.
 */
public interface AutoCloseableIterator<T> extends Iterator<T>, AutoCloseable {

    /**
     * Closes the iterator, performing any last-minute work.
     */
    @Override
    public void close();

    /**
     * @return true if the iterator has another element
     */
    @Override
    public boolean hasNext();

    /**
     * @return the next element in the iterator
     */
    @Override
    public T next();

    /**
     * @return an empty iterator that does nothing on close.
     */
    public static <T> AutoCloseableIterator<T> emptyIterator() {
        return new AutoCloseableIterator<T>() {

            @Override
            public void close() {
                // Do Nothing
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public T next() {
                return null;
            }

        };
    }

    /**
     * Wraps an {@code Iterator} that does nothing on close. Useful if you want to concatenate it
     * with an {@code AutoCloseableIterator}.
     * 
     * @param source the iterator to wrap
     * @return an {@code AutoCloseableIterator} that wraps the original
     */
    public static <T> AutoCloseableIterator<T> fromIterator(Iterator<T> source) {
        return new AutoCloseableIterator<T>() {

            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public T next() {
                return source.next();
            }

            @Override
            public void close() {
                // Do Nothing
            }

        };
    }

    /**
     * Transforms each element in the source iterator with the provided function.
     * 
     * @param source the source iterator
     * @param transformFunction the transformation function
     * @return an iterator with the type that matches the return type of the transformation function
     */
    public static <T, C> AutoCloseableIterator<C> transform(AutoCloseableIterator<T> source,
            Function<T, C> transformFunction) {
        return new AutoCloseableIterator<C>() {

            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public C next() {
                T nextObj = source.next();
                return transformFunction.apply(nextObj);
            }

            @Override
            public void close() {
                source.close();
            }
            
        };
    }

    /**
     * Filters an iterator to only include items that match the provided predicate function.
     * 
     * @param source the source iterator
     * @param filterFunction the predicate to test elements against
     * @return the filtered iterator
     */
    public static <T> AutoCloseableIterator<T> filter(AutoCloseableIterator<T> source,
            Predicate<T> filterFunction) {
        return new AutoCloseableIterator<T>() {

            T next = null;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    next = computeNext();
                }
                return next != null;
            }

            @Override
            public T next() {
                if (next == null && !hasNext()) {
                    throw new NoSuchElementException();
                }
                T returnValue = next;
                next = null;
                return returnValue;
            }

            @Override
            public void close() {
                source.close();
            }

            private T computeNext() {
                while (source.hasNext()) {
                    T sourceNext = source.next();
                    if (filterFunction.apply(sourceNext)) {
                        return sourceNext;
                    }
                }
                return null;
            }
        };
    }

    /**
     * Concatenates two {@code AutoCloseableIterators} into a single one, closing both when closed.
     * 
     * @param first the first iterator
     * @param second the second iterator
     * @return the concatenated iterator
     */
    public static <T> AutoCloseableIterator<T> concat(AutoCloseableIterator<T> first,
            AutoCloseableIterator<T> second) {
        return new AutoCloseableIterator<T>() {

            @Override
            public boolean hasNext() {
                return first.hasNext() || second.hasNext();
            }

            @Override
            public T next() {
                if (first.hasNext()) {
                    return first.next();
                }
                return second.next();
            }

            @Override
            public void close() {
                first.close();
                second.close();
            }

        };
    }
}


