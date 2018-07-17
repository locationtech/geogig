/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

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
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    protected VersionInfo _call() {
        return get();
    }

    public static VersionInfo get() {
        Properties properties = new Properties();
        VersionInfo info = null;
        try (InputStream resource = VersionInfo.class
                .getResourceAsStream("/git.geogig.properties")) {
            if (resource != null) {
                properties.load(resource);
                info = new VersionInfo(properties);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return info;
    }

}
