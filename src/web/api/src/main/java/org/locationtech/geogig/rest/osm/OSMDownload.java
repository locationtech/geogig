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

import java.io.File;
import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.osm.internal.OSMDownloadOp;
import org.locationtech.geogig.osm.internal.OSMReport;
import org.locationtech.geogig.osm.internal.OSMUpdateOp;
import org.locationtech.geogig.osm.internal.OSMUtils;
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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * Imports data from OSM using the Overpass API.
 * <ul>
 * <li>filter: Optional, or mandatory if {@code bbox} is not give. The filter file to use. Must
 * exist in the server filesystem and contain an <a
 * href="http://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL">Overpass QL filter</a>.
 * <li>bbox: Mandatory if {@code filter} is not given. The bounding box to use as filter, in WGS84
 * coordinates. Format: {@code <S>,<W>,<N>,<E>}.
 * <li>message: Message for the commit to create.
 * <li>update: Boolean. Default: false. Update the OSM data currently in the geogig repository
 * <li>rebase: Boolean. Default: false. Use rebase instead of merge when updating. Can only be true
 * if upate = true.
 * <li>mapping: The file that contains the data mapping to use".
 * </ul>
 *
 */
public class OSMDownload extends TransactionalResource {

    @Override
    public void init(org.restlet.Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(Variants.XML);
        getVariants().add(Variants.JSON);
    }

    @Override
    public Representation getRepresentation(final Variant variant) {
        final Request request = getRequest();
        Context context = getContext(request);

        Form options = getRequest().getResourceRef().getQueryAsForm();

        final String filterFileArg = options.getFirstValue("filter");
        final String bboxArg = options.getFirstValue("bbox");
        final String messageArg = options.getFirstValue("message");
        final boolean update = Boolean.valueOf(options.getFirstValue("update"));
        final boolean rebase = Boolean.valueOf(options.getFirstValue("rebase"));
        final String mappingFileArg = options.getFirstValue("mapping");

        checkArgSpec(filterFileArg != null ^ bboxArg != null || update,
                "You must specify a filter file or a bounding box");
        checkArgSpec((filterFileArg != null || bboxArg != null) ^ update,
                "Filters cannot be used when updating");

        checkArgSpec(context.index().isClean() && context.workingTree().isClean(),
                "Working tree and index are not clean");

        checkArgSpec(!rebase || update, "rebase switch can only be used when updating");

        final File filterFile = parseFile(filterFileArg);
        final File mappingFile = parseFile(mappingFileArg);
        final List<String> bbox = parseBbox(bboxArg);

        checkArgSpec(filterFile == null || filterFile.exists(),
                "The specified filter file does not exist");

        checkArgSpec(mappingFile == null || mappingFile.exists(),
                "The specified mapping file does not exist");

        AbstractGeoGigOp<Optional<OSMReport>> command;
        if (update) {
            command = context.command(OSMUpdateOp.class).setRebase(rebase).setMessage(messageArg)
                    .setAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT);
        } else {
            command = context.command(OSMDownloadOp.class).setBbox(bbox).setFilterFile(filterFile)
                    .setMessage(messageArg).setMappingFile(mappingFile)
                    .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT);
        }

        AsyncCommand<Optional<OSMReport>> asyncCommand;

        URI repo = context.repository().getLocation();
        String description = String
                .format("osm download filter: %s, bbox: %s, mapping: %s, update: %s, rebase: %s, repository: %s",
                        filterFileArg, bboxArg, mappingFileArg, update, rebase, repo);
        asyncCommand = AsyncContext.get().run(command, description);

        final String rootPath = request.getRootRef().toString();
        MediaType mediaType = variant.getMediaType();
        Representation rep = new OSMReportRepresentation(mediaType, asyncCommand, rootPath);
        return rep;
    }

    private void checkArgSpec(boolean expression, String messageHeader) throws CommandSpecException {
        if (!expression) {
            throw usageMessage(messageHeader);
        }
    }

    @Nullable
    private File parseFile(@Nullable String filePath) throws CommandSpecException {
        if (Strings.isNullOrEmpty(filePath)) {
            return null;
        }
        File f = new File(filePath);
        return f;
    }

    @Nullable
    private List<String> parseBbox(@Nullable String bboxArg) {
        if (bboxArg == null) {
            return null;
        }
        List<String> bbox = Splitter.on(",").splitToList(bboxArg);
        checkArgSpec(bbox.size() == 4, "Invalid bbox format: " + bboxArg + ". Expected S,W,N,E");
        double s = parseOrdinate(bbox.get(0));
        double w = parseOrdinate(bbox.get(1));
        double n = parseOrdinate(bbox.get(2));
        double e = parseOrdinate(bbox.get(3));
        checkArgSpec(n >= s, "South ordinate must be less than or equal to North ordinate");
        checkArgSpec(e >= w, "East ordinate must be less than or equal to West ordinate");
        return bbox;
    }

    private double parseOrdinate(String ordinate) {
        try {
            return Double.parseDouble(ordinate);
        } catch (NumberFormatException e) {
            throw usageMessage("Invalid ordinate: " + ordinate);
        }
    }

    private CommandSpecException usageMessage(String messageHeader) throws CommandSpecException {
        String msg = messageHeader
                + "\nUsage: GET <repo context>/osm/download?<[filter=<filterfile>]|[bbox=S,W,N,E]>[&message=<commit message>]"
                + "[&mapping=<mapping file>][&update=true|false*][&rebase=true|false*]\n"
                + "Arguments:\n"
                + " * filter: Optional, or mandatory if {@code bbox} is not give. The filter file to use. Must exist in the server filesystem and contain an Overpass QL filter.\n"
                + " * bbox: Mandatory if {@code filter} is not given. The bounding box to use as filter, in WGS84 coordinates. Format: {@code <S>,<W>,<N>,<E>}.\n"
                + " * message: Message for the commit to create.\n"
                + " * update: Boolean. Default: false. Update the OSM data currently in the geogig repository.\n"
                + " * rebase: Boolean. Default: false. Use rebase instead of merge when updating. Can only be true of update is true.\n"
                + " * mapping: The file that contains the data mapping to use\n";
        throw new CommandSpecException(msg);
    }

}
