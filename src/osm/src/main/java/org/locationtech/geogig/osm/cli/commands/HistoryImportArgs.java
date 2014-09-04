/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import java.io.File;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;

/**
 *
 */
public class HistoryImportArgs {

    @Parameter(arity = 1, description = "<OSM api URL. eg: http://api.openstreetmap.org/api/0.6>", required = false)
    public List<String> apiUrl = Lists.newArrayList(0);

    @Parameter(names = "--from", description = "initial changeset id.")
    public long startIndex = 1;

    @Parameter(names = "--resume", description = "Resume import from last imported changeset on the current branch.")
    public boolean resume;

    @Parameter(names = "--to", description = "final changeset id.")
    public long endIndex = 1000;

    @Parameter(names = "--saveto", description = "Directory where to save the changesets. Defaults to $TMP/changesets.osm")
    public File saveFolder;

    @Parameter(names = { "--numthreads", "-t" }, description = "Number of threads to use to fetch changesets. Must be between 1 and 6")
    public int numThreads = 4;

    @Parameter(names = "--dev", description = "Use the development test api endpoint <http://api06.dev.openstreetmap.org/api/0.6>. NOTE: this is not the real osm history, but just for testing purposes. ")
    public boolean useTestApiEndpoint = false;

    @Parameter(hidden = true, names = "--bbox", description = "Filter by bounding box <min lon,min lat, max lon, max lat>")
    public String bbox;

    public static final String DEVELOPMENT_API_ENDPOINT = "http://api06.dev.openstreetmap.org/api/0.6";

    public static final String DEFAULT_API_ENDPOINT = "http://api.openstreetmap.org/api/0.6";

}
