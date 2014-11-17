/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.di.CanRunDuringConflict;

import com.google.common.base.Throwables;

/**
 * Retrieves GeoGig version information.
 * 
 */
@CanRunDuringConflict
public class VersionOp extends AbstractGeoGigOp<VersionInfo> {

    /**
     * Executes the Version operation.
     * 
     * @return the version info of the current build
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    protected VersionInfo _call() {
        Properties properties = new Properties();
        VersionInfo info = null;
        try {
            InputStream resource = getClass().getClassLoader()
                    .getResourceAsStream("git.properties");
            if (resource != null) {
                properties.load(resource);
                info = new VersionInfo(properties);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return info;

    }

}
