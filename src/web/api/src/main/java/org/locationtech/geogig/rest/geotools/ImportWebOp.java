/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import java.util.function.Function;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.resource.Representation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;

/**
 * Base class for Import Operations.
 */
public class ImportWebOp extends AbstractWebAPICommand {

    /**
     * Request Parameter keys.
     */
    public static final String TABLE_KEY = "table";
    public static final String ALL_KEY = "all";
    public static final String ADD_KEY = "add";
    public static final String ALTER_KEY = "alter";
    public static final String FORCE_FEAT_KEY = "forceFeatureType";
    public static final String DEST_PATH_KEY = "dest";
    public static final String FID_ATTR_KEY = "fidAttribute";
    public static final String AUTHOR_NAME_KEY = "authorName";
    public static final String AUTHOR_EMAIL_KEY = "authorEmail";
    public static final String COMMIT_MSG_KEY = "message";

    private final ParameterSet options;

    @VisibleForTesting
    public AsyncContext asyncContext;

    public ImportWebOp(ParameterSet options) {
        super(options);
        this.options = options;
    }
    
    @Override
    public void runInternal(CommandContext context) {
        String requestFormat = options.getFirstValue("format");
        if (null == requestFormat) {
            // no "format" parameter requested
            throw new CommandSpecException("missing required \"format\" parameter");
        }
        // get the import context from the requested parameters
        ImportContextService ctxService = ImportContextServiceFactory.getContextService(
            requestFormat);
        // build DataStore from options
        final Supplier<DataStore> datastore = ctxService.getDataStore(options);
        DataStoreImportOp command = buildImportOp(datastore, context);
        final String commandDescription = ctxService.getCommandDescription();
        if (asyncContext == null) {
            asyncContext = AsyncContext.get();
        }
        final AsyncContext.AsyncCommand<?> asyncCommand = asyncContext.run(command,
            commandDescription);

        Function<MediaType, Representation> rep = new Function<MediaType, Representation>() {

            private final String baseUrl = context.getBaseURL();

            @Override
            public Representation apply(MediaType mediaType) {
                AsyncCommandRepresentation<?> repr;
                repr = Representations.newRepresentation(asyncCommand, mediaType, baseUrl);
                return repr;
            }
        };

        context.setResponse(rep);
    }

    @Override
    public boolean supports(Method method) {
        return Method.POST.equals(method);
    }

    private DataStoreImportOp buildImportOp(final Supplier<DataStore> datastore,
            CommandContext context) {
        // collect Import parameters
        final String table = options.getFirstValue(TABLE_KEY);
        final boolean all = Boolean.valueOf(options.getFirstValue(ALL_KEY, "false"));
        final boolean add = Boolean.valueOf(options.getFirstValue(ADD_KEY, "false"));
        final boolean forceFeatureType = Boolean
            .valueOf(options.getFirstValue(FORCE_FEAT_KEY, "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue(ALTER_KEY, "false"));
        final String dest = options.getFirstValue(DEST_PATH_KEY);
        final String fidAttribute = options.getFirstValue(FID_ATTR_KEY);
        final String authorName = options.getFirstValue(AUTHOR_NAME_KEY);
        final String authorEmail = options.getFirstValue(AUTHOR_EMAIL_KEY);
        final String commitMessage = options.getFirstValue(COMMIT_MSG_KEY);
        // validate the request
        // ImportOp must specify a TABLE or ALL, not both
        if ((null == table && !all) || (null != table && all)) {
            throw new CommandSpecException(
                "Request must specify a table name (table=name) or ALL "
                + "(all=true)");
        }
        // regardless, we have to create the DataStoreImportOp
        DataStoreImportOp command;
        // Import will require a transaction. If there isn't a transaction already active, create
        // one and wrape the Import command with transaction management.
        GeogigTransaction transaction;
        // if we are creating a transaction, we need the operation to start/end it automatically
        final boolean handleTxn = this.getTransactionId() == null;
        if (handleTxn) {
            // we must create a transaction
            transaction = this.getCommandLocator(context).command(TransactionBegin.class).call();
            // build the transaction wrapped command
            command = transaction.command(TransactionWrappedDataStoreImportOp.class);
        } else {
            // we already have a transaction
            transaction = GeogigTransaction.class.cast(this.getCommandLocator(context));
            // build the normal command
            command = transaction.command(DataStoreImportOp.class);
        }
        // set all the import op parameters
        return command.setDataStore(datastore).setTable(table).setAll(all).setAdd(add)
            .setForceFeatureType(forceFeatureType).setAlter(alter)
            .setDest(dest).setFidAttribute(fidAttribute).setAuthorEmail(authorEmail).setAuthorName(
            authorName).setCommitMessage(commitMessage);
    }

    private static class TransactionWrappedDataStoreImportOp extends DataStoreImportOp {

        @Override
        protected RevCommit _call() {
            // the context better be a GeogigTransaction
            GeogigTransaction txnContext = GeogigTransaction.class.cast(context);
            RevCommit revCommit =  null;
            try {
                // call the import
                revCommit = super._call();
                // end the transaction
                txnContext.commit();
            } catch (Exception ex) {
                // abort the transaction
                txnContext.abort();
                throw ex;
            }
            return revCommit;
        }
    }
}
