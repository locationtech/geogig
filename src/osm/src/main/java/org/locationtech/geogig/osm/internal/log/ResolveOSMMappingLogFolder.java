/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal.log;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;

import com.google.common.base.Throwables;

/**
 * Resolves the location of the {@code osm} mapping log folder directory relative to the
 * {@link Platform#pwd() current directory}.
 * <p>
 * If the folder is not found, it will be created.
 * 
 */
public class ResolveOSMMappingLogFolder extends AbstractGeoGigOp<File> {

    @Override
    protected File _call() {
        final URL geogigDirUrl = command(ResolveGeogigDir.class).call().get();
        File repoDir;
        try {
            repoDir = new File(geogigDirUrl.toURI());
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
        File osmMapFolder = new File(new File(repoDir, "osm"), "map");
        if (!osmMapFolder.exists()) {
            if (!osmMapFolder.mkdirs()) {
                throw new IllegalStateException("Could not create osm mapping log folder");
            }
        }
        return osmMapFolder;

    }
}
