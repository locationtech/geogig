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

import java.net.URI;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.TransactionalResource;
import org.locationtech.geogig.rest.Variants;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

/**
 * Base class for Import Operations.
 */
public abstract class ImportWebOp extends TransactionalResource {

    @Override
    public void init(org.restlet.Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(Variants.XML);
        getVariants().add(Variants.JSON);
    }

    @Override
    public Representation getRepresentation(final Variant variant) {
        final Request request = getRequest();
        final Context context = super.getContext(request);

        Form options = getRequest().getResourceRef().getQueryAsForm();

        DataStore dataStore = getDataStore(options);

        final String table = options.getFirstValue("table");
        final boolean all = Boolean.valueOf(options.getFirstValue("all", "false"));
        final boolean add = Boolean.valueOf(options.getFirstValue("add", "false"));
        final boolean forceFeatureType = Boolean
            .valueOf(options.getFirstValue("forceFeatureType", "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue("alter", "false"));
        final String dest = options.getFirstValue("dest");
        final String fidAttrib = options.getFirstValue("fidAttrib");
        ImportOp command = context.command(ImportOp.class);
        command.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
            .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
            .setDestinationPath(dest).setFidAttribute(fidAttrib);

        AsyncContext.AsyncCommand<RevTree> asyncCommand;

        URI repo = context.repository().getLocation();
        asyncCommand = AsyncContext.get().run(command, getCommandDescription(table, all, repo));

        final String rootPath = request.getRootRef().toString();
        MediaType mediaType = variant.getMediaType();
        return new RevTreeRepresentation(mediaType, asyncCommand, rootPath);
    }

    /**
     * Create a DataStore from Form options.
     *
     * @param options Form parameters with which to configure the DataStore.
     * @return A DataStore based on supplied options.
     */
    public abstract DataStore getDataStore(final Form options);

    /**
     * Returns a String representation for this ImportOp command.
     *
     * @param table Table name to be imported.
     * @param all - true if all tables should be imported
     * @param repo - URI representation of the repo into which the data should be imported.
     * @return A String representation of the command.
     */
    public abstract String getCommandDescription(String table, boolean all, URI repo);
}
