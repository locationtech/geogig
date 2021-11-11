/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.transaction;

import java.util.Optional;
import java.util.UUID;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

/**
 * Resolves a provided UUID to a {@link GeogigTransaction} if it exists, or {@link Optional#empty()}
 * otherwise.
 *
 * @see GeogigTransaction
 */
public class TransactionResolve extends AbstractGeoGigOp<Optional<GeogigTransaction>> {

    private UUID id = null;

    /**
     * @param id the UUID representation of the transaction id to resolve
     * @return {@code this}
     */
    public TransactionResolve setId(UUID id) {
        this.id = id;
        return this;
    }

    /**
     * @return the resolved {@link GeogigTransaction}, or {@link Optional#empty()} if it could not
     *         be resolved
     */
    protected @Override Optional<GeogigTransaction> _call() {
        Preconditions.checkState(!(context instanceof GeogigTransaction),
                "Cannot resolve a transaction within a transaction!");
        Preconditions.checkArgument(id != null, "No id was specified to resolve!");

        final String transactionNamespace = GeogigTransaction.buildTransactionNamespace(id);

        GeogigTransaction transaction = null;
        if (!context.refDatabase().getAll(transactionNamespace).isEmpty()) {
            transaction = new GeogigTransaction(context, id);
        }

        return Optional.ofNullable(transaction);
    }
}
