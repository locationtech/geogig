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

import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Command for Geotools imports through the WEB API.
 * <p>
 * Concrete format options are handled by implementations of {@link DataStoreImportContextService},
 * provided by the {@link DataStoreImportContextServiceFactory}. All
 * {@link DataStoreImportContextService} implementations should be listed in
 * <b> {@code META-INF/services/org.locationtech.geogig.rest.geotools.DataStoreImportContextService}
 * </b>
 * <p>
 * The {@link DataStoreImportContextService} provides the data source where to import from. This
 * operation always runs inside a transaction. If the transaction identifier has been provided (
 * {@link #setTransactionId(String)}), this operation just uses it and doens't commit the
 * transaction. If no transaction id has been provided, this operation creates a new transaction and
 * commits it at the end of the operation. This is to ensure atomicity as this operation performs
 * the {@link ImportOp import}, {@link AddOp staging}, and {@link CommitOp commit} of the imported
 * layers.
 * <p>
 * Request Parameters:
 * <ul>
 * <li><b>format</b>: Mandatory, input format for the imported data. Currently only {@code GPKG} is
 * supported. Format argument value is case insensitive.
 * <li><b>layer</b>: Optional. If present, only import the layer table from the provided DataStore
 * where the table name matches the value provided. If not present, import all layer tables from the
 * provided DataStore.
 * <li><b>dest</b>: Optional. The name of the feature tree path in the repository into which the
 * features should be imported. If not present, the tree path will be derived from the imported
 * feature type name (e.g. table name if importing from a database, file name if importing from a
 * shapefile, and so on).
 * <li><b>add</b>: Optional. If <i>true</i>, import features from the provided DataStore on top of
 * the existing feature type tree, if such exists. If <b>false</b>, replace the whole feature type
 * tree with the features being imported (i.e. first delete the feature tree in the repository and
 * then import all new features).
 * <li><b>alter</b>: Optional. If present and set to <i>true</i>, set the default feature type of
 * the repository path destination to match the feature type of the features being imported, and
 * <i>alter</i> the feature type of all features in the destination to match the feature type of the
 * features being imported. If not present, or set to <i>false</i>, do not alter the destination
 * feature type.
 * <li><b>forceFeatureType</b>: Optional. If present and set to <i>true</i>, use the feature type of
 * the features being imported, even if it doesn't match the default feature type of the
 * destination.
 * <li><b>fidAttribute</b>: Optional. If present, use the Attribute indicated by this value when
 * creating Feature Ids. If absent, use the default for creating Feature Ids.
 * <li><b>authorName</b>: Optional, but highly recommended. Specifies the author name to use for the
 * resulting commit.
 * <li><b>authorEmail</b>: Optional, but highly recommended. Specifies the author email to use for
 * the resulting commit.
 * <li><b>message</b>: Optional, but highly recommended. Specifies the commit message to use for the
 * resulting commit.
 * </ul>
 * <p>
 * <b>NOTE 1</b>: {@link DataStoreImportContextService} implementations may add additional format
 * specific request parameters.
 * <p>
 * <b>NOTE 2</b>: The file uploaded is added into the {@link ParameterSet} that is passed to the
 * constructor of instances of this operation. {@link DataStoreImportContextService} implementations
 * may need to access this upload to perform format specific functions, so it is passed along.
 * <p>
 * Usage: <br>
 * <b>
 * {@code POST <repository URL>/import[.xml|.json]?format=<format name>[&layer=<layer table name>][&dest=
 * <destination path][&alter=<true|false>][&forceFeatureType=<true|false>
 * ][&fidAaaattribute=<attribute name>][&authorEmail=<email address>][&authorName=<author name>][&message=<commit message>]}
 * </b>
 * <p>
 */
public class Import extends AbstractWebAPICommand {

    // Request Parameter keys.
    /**
     * Request parameter indicating the import format.
     */
    public static final String FORMAT_KEY = "format";

    /**
     * Request parameter indicating the name of the layer table to import.
     */
    public static final String LAYER_KEY = "layer";

    /**
     * Request parameter indicating if features should only be added (true) or replace the whole
     * feature tree.
     */
    public static final String ADD_KEY = "add";

    /**
     * Request parameter indicating if destination tables should be altered to match the feature
     * type of the imported features or not.
     */
    public static final String ALTER_KEY = "alter";

    /**
     * Request parameter indicating if the imported feature type should be used even if it does not
     * match the default feature type of the destination.
     */
    public static final String FORCE_FEAT_KEY = "forceFeatureType";

    /**
     * Request parameter indicating the destination path name if it should be different than the
     * path of the features being imported.
     */
    public static final String DEST_PATH_KEY = "dest";

    /**
     * Request parameter indicating the feature attribute to use when generating Feature Ids if the
     * default Id creation should be overridden.
     */
    public static final String FID_ATTR_KEY = "fidAttribute";

    /**
     * Request parameter indicating the author name to use for the resulting commit.
     */
    public static final String AUTHOR_NAME_KEY = "authorName";

    /**
     * Request parameter indicating the author email to use for the resulting commit.
     */
    public static final String AUTHOR_EMAIL_KEY = "authorEmail";

    /**
     * Request parameter indicating the message to use for the resulting commit.
     */
    public static final String COMMIT_MSG_KEY = "message";

    /**
     * Request parameter indicating the branch to import the features onto.
     */
    public static final String ROOT_KEY = "root";

    private ParameterSet options;

    @VisibleForTesting
    public AsyncContext asyncContext;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        this.options = options;
    }

    @Override
    public void runInternal(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, import requires a transaction to preserve the stability of the repository.");
        }
        final String requestFormat = options.getFirstValue(FORMAT_KEY);
        Preconditions.checkArgument(requestFormat != null, "missing required 'format' parameter");

        final Context transaction = this.getRepositoryContext(context);

        // get the import context from the requested parameters
        DataStoreImportContextService ctxService = DataStoreImportContextServiceFactory
                .getContextService(requestFormat);
        // build DataStore from options
        DataStoreImportOp<?> command = buildImportOp(ctxService, transaction);
        final String commandDescription = ctxService.getCommandDescription();
        if (asyncContext == null) {
            asyncContext = AsyncContext.get();
        }
        final AsyncContext.AsyncCommand<?> asyncCommand = asyncContext.run(command,
                commandDescription);

        context.setResponseContent(Representations.newRepresentation(asyncCommand, false));
    }

    @Override
    public boolean supports(RequestMethod method) {
        return RequestMethod.POST.equals(method);
    }

    private DataStoreImportOp<?> buildImportOp(
            final DataStoreImportContextService ctxService, Context context) {
        // collect Import parameters
        final String layerTableName = options.getFirstValue(LAYER_KEY);
        final boolean all = layerTableName == null;
        final boolean add = Boolean.valueOf(options.getFirstValue(ADD_KEY, "false"));
        final boolean forceFeatureType = Boolean
                .valueOf(options.getFirstValue(FORCE_FEAT_KEY, "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue(ALTER_KEY, "false"));
        final String dest = options.getFirstValue(DEST_PATH_KEY);
        final String root = options.getFirstValue(ROOT_KEY);
        final String fidAttribute = options.getFirstValue(FID_ATTR_KEY);
        final String authorName = options.getFirstValue(AUTHOR_NAME_KEY);
        final String authorEmail = options.getFirstValue(AUTHOR_EMAIL_KEY);
        final String commitMessage = options.getFirstValue(COMMIT_MSG_KEY);
        // regardless, we have to create the DataStoreImportOp
        DataStoreImportOp<?> command = ctxService.createCommand(context, options);
        // set all the import op parameters
        return command.setDataStore(ctxService.getDataStore(options)).setTable(layerTableName)
                .setRoot(root).setAll(all).setAdd(add).setForceFeatureType(forceFeatureType)
                .setAlter(alter).setDest(dest).setFidAttribute(fidAttribute)
                .setAuthorEmail(authorEmail).setAuthorName(authorName)
                .setCommitMessage(commitMessage);
    }
}
