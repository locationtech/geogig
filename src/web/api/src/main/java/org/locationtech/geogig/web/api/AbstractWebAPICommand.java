/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.UUID;

import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Optional;

/**
 * An abstract command that allows WebAPICommands to support long transactions.
 */

public abstract class AbstractWebAPICommand implements WebAPICommand {

    private UUID transactionId = null;

    protected AbstractWebAPICommand() {
    }

    protected AbstractWebAPICommand(ParameterSet options) {
        setParameters(options);
    }

    @Override
    public void setParameters(ParameterSet options) {
        setTransactionId(options.getFirstValue("transactionId", null));
        setParametersInternal(options);
    }

    protected abstract void setParametersInternal(ParameterSet options);

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.GET.equals(method);
    }

    /**
     * Check for commands that require an open repository.
     * 
     * @return whether or not this command requires an open repository.
     */
    public boolean requiresOpenRepo() {
        return true;
    }

    /**
     * Check for commands that require a transaction.
     * 
     * @return whether or not this command requires a transaction.
     */
    public boolean requiresTransaction() {
        return true;
    }

    /**
     * Accessor for the transactionId
     * 
     * @return the id of the transaction to run commands off of
     */
    public UUID getTransactionId() {
        return transactionId;
    }

    /**
     * Mutator for the transactionId
     * 
     * @param transactionId - the transaction id to run commands off of
     */
    public void setTransactionId(String transactionId) {
        if (transactionId != null) {
            this.transactionId = UUID.fromString(transactionId);
        }
    }


    /**
     * This function either builds a GeoGigTransaction to run commands off of if there is a
     * transactionId to build off of or the GeoGig commandLocator otherwise.
     * 
     * @param context - the context to get the information needed to get the commandLocator
     * @return
     */
    public Context getRepositoryContext(CommandContext context) {
        if (requiresTransaction() || transactionId != null) {
            return getTransactionContext(context);
        }
        return context.getRepository().context();
    }

    private Context getTransactionContext(CommandContext context) {
        if (transactionId == null) {
            throw new CommandSpecException(
                    "No transaction was specified, this command requires a transaction to preserve the stability of the repository.");
        } else {
            Optional<GeogigTransaction> transaction = context.getRepository()
                    .command(TransactionResolve.class).setId(transactionId).call();
            if (transaction.isPresent()) {
                return transaction.get();
            } else {
                throw new CommandSpecException(
                        "A transaction with the provided ID could not be found.",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Override
    public void run(CommandContext context) {
        Repository repo = context.getRepository();
        if (requiresOpenRepo() && (null == repo || !repo.isOpen())) {
            throw new CommandSpecException("Repository not found.", HttpStatus.NOT_FOUND);
        }
        runInternal(context);
    }

    protected abstract void runInternal(CommandContext context);

    /**
     * Parses a string to an Integer, using a default value if the was not found in the parameter
     * set.
     * 
     * @param form the parameter set
     * @param key the attribute key
     * @param defaultValue the default value
     * @return the parsed integer
     */
    protected static Integer parseInt(ParameterSet form, String key, Integer defaultValue) {
        String val = form.getFirstValue(key);
        Integer retval = defaultValue;
        if (val != null) {
            try {
                retval = new Integer(val);
            } catch (NumberFormatException nfe) {
                throw new CommandSpecException(
                        "Invalid value '" + val + "' specified for option: " + key);
            }
        }
        return retval;
    }
}
