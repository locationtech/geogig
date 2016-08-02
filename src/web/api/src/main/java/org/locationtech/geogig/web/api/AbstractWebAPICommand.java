/* Copyright (c) 2013-2014 Boundless and others.
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

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.GeogigTransaction;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.RestletException;
import org.restlet.data.Method;
import org.restlet.data.Status;

/**
 * An abstract command that allows WebAPICommands to support long transactions.
 */

public abstract class AbstractWebAPICommand implements WebAPICommand {

    private UUID transactionId = null;

    private Status commandStatus = Status.SUCCESS_OK;

    protected AbstractWebAPICommand(ParameterSet options) {
        setTransactionId(options.getFirstValue("transactionId", null));
    }

    @Override
    public boolean supports(final Method method) {
        return Method.GET.equals(method);
    }

    /**
     * Check for commands that require an open repository.
     * 
     * @return whether or not this command requires an open repository.
     */
    protected boolean requiresOpenRepo() {
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
     * Accessor for the status of the command
     * 
     * @return the command status.
     */
    @Override
    public Status getStatus() {
        return commandStatus;
    }

    /**
     * Mutator for the command status.
     * 
     * @param status - the status of the command
     */
    protected void setStatus(Status status) {
        this.commandStatus = status;
    }

    /**
     * This function either builds a GeoGigTransaction to run commands off of if there is a
     * transactionId to build off of or the GeoGig commandLocator otherwise.
     * 
     * @param context - the context to get the information needed to get the commandLocator
     * @return
     */
    public Context getCommandLocator(CommandContext context) {
        if (transactionId != null) {
            return new GeogigTransaction(context.context(), transactionId);
        }
        return context.context();
    }

    public void run(CommandContext context) {
        Repository repo = context.getRepository();
        if (requiresOpenRepo() && (null == repo || !repo.isOpen())) {
            throw new RestletException("Repository not found.",
                    org.restlet.data.Status.CLIENT_ERROR_NOT_FOUND);
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
