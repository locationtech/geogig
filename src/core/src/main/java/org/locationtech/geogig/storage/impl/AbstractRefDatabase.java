/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.locationtech.geogig.storage.RefDatabase;

/**
 * Provides a base implementation for different representations of the {@link RefDatabase}.
 * 
 * @see RefDatabase
 */
public abstract class AbstractRefDatabase implements RefDatabase {

    Lock lock = new ReentrantLock();

    /**
     * Locks access to the main repository refs.
     * 
     * @throws TimeoutException
     */
    @Override
    public final void lock() throws TimeoutException {
        try {
            if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                throw new TimeoutException("The attempt to lock the database timed out.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unlocks access to the main repository refs.
     */
    @Override
    public final void unlock() {
        lock.unlock();
    }

}
