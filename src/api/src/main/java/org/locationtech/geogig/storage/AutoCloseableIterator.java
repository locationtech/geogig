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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

/**
 * Interface for an iterator that can do some cleanup or other work when it is no longer needed. Can
 * be used in conjunction with a try-with-resources block.
 * 
 * @since 1.0
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
        if (source instanceof AutoCloseableIterator) {
            return (AutoCloseableIterator<T>) source;
        }
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

    public static <T, I extends Iterator<T>> AutoCloseableIterator<T> fromIterator(I source,
            Consumer<I> closeAction) {

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
                closeAction.accept(source);
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
    public static <T, C> AutoCloseableIterator<C> transform(
            AutoCloseableIterator<? extends T> source, Function<T, C> transformFunction) {
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
    public static <T> AutoCloseableIterator<T> filter(AutoCloseableIterator<? extends T> source,
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

    public static <T> AutoCloseableIterator<T> concat(AutoCloseableIterator<Iterator<T>> its) {
        Iterator<T> concatenated = Iterators.concat(its);

        return new AutoCloseableIterator<T>() {

            @Override
            public boolean hasNext() {
                return concatenated.hasNext();
            }

            @Override
            public T next() {
                return concatenated.next();
            }

            @Override
            public void close() {
                its.close();
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

    public static <T> AutoCloseableIterator<T> limit(final AutoCloseableIterator<T> iterator,
            final int limit) {

        return new AutoCloseableIterator<T>() {
            private int count;

            @Override
            public boolean hasNext() {
                return count < limit && iterator.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                count++;
                return iterator.next();
            }

            @Override
            public void close() {
                iterator.close();
            }
        };
    }

    public static <T> AutoCloseableIterator<List<T>> partition(AutoCloseableIterator<T> iterator,
            int partitionSize) {

        return new AutoCloseableIterator<List<T>>() {

            @Override
            public void close() {
                iterator.close();
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<T> list = new ArrayList<>(partitionSize);
                for (int i = 0; i < partitionSize && iterator.hasNext(); i++) {
                    T next = iterator.next();
                    list.add(next);
                }
                return list;
            }
        };
    }
}
