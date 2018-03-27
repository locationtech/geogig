/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;

class Index<T extends Comparable<T>> {

    // using two separate maps is much faster than one single BiMap
    private Map<T, Integer> valueMap = new HashMap<>();

    private Map<Integer, T> indexMap = new HashMap<>();

    private AtomicInteger sequence = new AtomicInteger();

    public int getOrAdd(T value) {
        Preconditions.checkNotNull(value);
        Integer index = valueMap.get(value);
        if (index == null) {
            index = sequence.getAndIncrement();
            valueMap.put(value, index);
            indexMap.put(index, value);
        }
        return index.intValue();
    }

    public int indexOf(T value) {
        Preconditions.checkNotNull(value);
        Integer index = valueMap.get(value);
        return index == null ? -1 : index.intValue();
    }

    public T get(int index) throws NoSuchElementException {
        T value = indexMap.get(Integer.valueOf(index));
        if (value == null) {
            throw new NoSuchElementException("No element at index " + index);
        }
        return value;
    }

    public int size() {
        return valueMap.size();
    }

    public int get(T value) {
        Integer index = valueMap.get(value);
        return index == null ? -1 : index.intValue();
    }
}
