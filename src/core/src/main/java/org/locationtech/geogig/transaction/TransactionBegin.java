/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.transaction;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefDatabase;

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
    protected @Override GeogigTransaction _call() {
        Preconditions.checkState(!(context instanceof GeogigTransaction),
                "Cannot start a new transaction within a transaction!");

        final Context nonTransactionContext = context();
        final RefDatabase nonTxRefdb = nonTransactionContext.refDatabase();

        final UUID transactionId = UUID.randomUUID();
        final GeogigTransaction transactionContext = new GeogigTransaction(context, transactionId);
        // Lock the repository
        try {
            nonTxRefdb.lock();
            transactionContext.create();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            // Unlock the repository
            nonTxRefdb.unlock();
        }
        // Return the transaction
        return transactionContext;
    }
}
