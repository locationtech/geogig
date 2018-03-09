/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.base.Preconditions;

/**
 * Creates a new {@link GeogigTransaction} and copies all of the repository refs for that
 * transaction to use.
 * 
 * @see GeogigTransaction
 */
@Hookable(name = "transaction-start")
public class TransactionBegin extends AbstractGeoGigOp<GeogigTransaction> {

    /**
     * Creates a new transaction and returns it.
     * 
     * @return the {@link GeogigTransaction} that was created by the operation
     */
    @Override
    protected GeogigTransaction _call() {
        Preconditions.checkState(!(context instanceof GeogigTransaction),
                "Cannot start a new transaction within a transaction!");

        GeogigTransaction t = new GeogigTransaction(context, UUID.randomUUID());

        // Lock the repository
        try {
            refDatabase().lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        try {
            // Copy original refs
            t.create();
        } finally {
            // Unlock the repository
            refDatabase().unlock();
        }
        // Return the transaction
        return t;
    }
}
