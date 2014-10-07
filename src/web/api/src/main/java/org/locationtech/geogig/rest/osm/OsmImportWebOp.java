/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.osm;

import java.net.URL;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.OSMImportOp;
import org.locationtech.geogig.osm.internal.OSMReport;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.TransactionalResource;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

public class OsmImportWebOp extends TransactionalResource {

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

        final String urlOrFilepath = options.getFirstValue("uri");
        final boolean add = Boolean.valueOf(options.getFirstValue("add"));
        final String mappingFile = options.getFirstValue("mapping");
        Mapping mapping = null;
        if (mappingFile != null) {
            mapping = Mapping.fromFile(mappingFile);
        }
        final boolean noRaw = Boolean.valueOf(options.getFirstValue("noRaw"));
        final String message = options.getFirstValue("message");

        if (urlOrFilepath == null) {
            String msg = "Missing parameter: uri\n"
                    + "Usage: GET <repo context>/osm/import?uri=<osm file URI>[&<arg>=<value>]+\n"
                    + "Arguments:\n"
                    + " * uri: Mandatory. URL or path to OSM data file in the server filesystem\n"
                    + " * add: Optional. true|false. Default: false. If true, do not remove previous data before importing.\n"
                    + " * mapping: Optional. Location of mapping file in the server filesystem\n"
                    + " * noRaw: Optional. true|false. Default: false. If true, do not import raw data when using a mapping\n"
                    + " * message: Optional. Message for the commit to create.";

            throw new CommandSpecException(msg);
        }

        OSMImportOp command = context.command(OSMImportOp.class);
        command.setAdd(add);
        command.setDataSource(urlOrFilepath);
        command.setMapping(mapping);
        command.setMessage(message);
        command.setNoRaw(noRaw);

        AsyncCommand<Optional<OSMReport>> asyncCommand;

        URL repo = context.repository().getLocation();
        String description = String.format("osm import %s, repository: %s", urlOrFilepath, repo);
        asyncCommand = AsyncContext.get().run(command, description);

        final String rootPath = request.getRootRef().toString();
        MediaType mediaType = variant.getMediaType();
        Representation rep = new OSMReportRepresentation(mediaType, asyncCommand, rootPath);
        return rep;
    }
}
