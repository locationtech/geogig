/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.storage.RefDatabase;

import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingRefDatabase implements RefDatabase {

    protected final RefDatabase actual;

    public @Override void open() {
        actual.open();
    }

    public @Override void lock() throws TimeoutException {
        actual.lock();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override void unlock() {
        actual.unlock();
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override String getRef(String name) throws IllegalArgumentException {
        return actual.getRef(name);
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override void checkWritable() {
        actual.checkWritable();
    }

    public @Override void checkOpen() {
        actual.checkOpen();
    }

    public @Override String getSymRef(String name) throws IllegalArgumentException {
        return actual.getSymRef(name);
    }

    public @Override void putRef(String refName, String refValue) {
        actual.putRef(refName, refValue);
    }

    public @Override void putSymRef(String name, String val) {
        actual.putSymRef(name, val);
    }

    public @Override String remove(String refName) {
        return actual.remove(refName);
    }

    public @Override Map<String, String> getAll() {
        return actual.getAll();
    }

    public @Override Map<String, String> getAll(String prefix) {
        return actual.getAll(prefix);
    }

    public @Override Map<String, String> removeAll(String namespace) {
        return actual.removeAll(namespace);
    }

}
