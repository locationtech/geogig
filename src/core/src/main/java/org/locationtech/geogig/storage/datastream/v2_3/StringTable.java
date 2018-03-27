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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

abstract class StringTable {

    public static final StringTable EMPTY = new Immutable(ImmutableList.of());

    /**
     * Factory method for a mutable string table that ensures it contains no duplicates
     */
    public static StringTable unique() {
        return new Unique();
    }

    /**
     * Adds a string to the string table and returns the index the string can be retrieved later
     * through {@link #get(int)}
     * <p>
     * If the implementation doesn't allow duplicates and the same string already exists in the
     * string table, then the existing index is returned, otherwise a new entry is created and the
     * new index is returned.
     */
    public abstract int add(String s);

    /**
     * Returns the String associated to the given index
     * 
     * @throws NoSuchElementException if there's no entry for the given index
     */
    public abstract String get(int index) throws NoSuchElementException;

    public abstract int get(String value);
    
    /**
     * @return number of entries in the string table
     */
    public abstract int size();

    public void encode(DataOutput out) {
        final int tableSize = size();
        try {
            Varint.writeUnsignedVarInt(tableSize, out);
            for (int i = 0; i < tableSize; i++) {
                String s = get(i);
                byte[] bytes = s.getBytes(Charsets.UTF_8);
                int len = bytes.length;
                Varint.writeUnsignedVarInt(len, out);
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads an <b>immutable<b> string table out of the given input stream
     */
    public static StringTable decode(DataInput in) {
        List<String> arr;
        try {
            final int arrLen = Varint.readUnsignedVarInt(in);
            arr = new ArrayList<>(arrLen);
            for (int i = 0; i < arrLen; i++) {
                int len = Varint.readUnsignedVarInt(in);
                byte[] strBytes = new byte[len];
                in.readFully(strBytes);
                String s = new String(strBytes, Charsets.UTF_8);
                arr.add(s);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return StringTable.of(arr);
    }

    /**
     * Factory method to create an immutable string table containing the given strings
     */
    private static StringTable of(List<String> table) {
        return new Immutable(table);
    }

    private static class Unique extends StringTable {

        private Index<String> unique = new Index<>();

        @Override
        public int add(String s) {
            int index = unique.getOrAdd(s);
            return index;
        }

        @Override
        public String get(int index) throws NoSuchElementException {
            String val = unique.get(index);
            return val;
        }

        @Override
        public int size() {
            return unique.size();
        }

        @Override
        public int get(String value) {
            return unique.get(value);
        }
    }

    static final class Immutable extends StringTable {
        final List<String> table;

        Immutable(List<String> table) {
            this.table = ImmutableList.copyOf(table);
        }

        @Override
        public int add(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String get(int index) throws NoSuchElementException {
            if (index >= table.size()) {
                throw new NoSuchElementException(
                        "Index out of bounds: " + index + ", max index: " + (table.size() - 1));
            }
            return table.get(index);
        }

        @Override
        public int size() {
            return table.size();
        }

        @Override
        public int get(String value) {
            return table.indexOf(value);
        }
    }
}
