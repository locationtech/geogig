/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.cli;

import org.locationtech.geogig.cli.CLIModule;
import org.locationtech.geogig.geotools.cli.geojson.GeoJsonCommandProxy;
import org.locationtech.geogig.geotools.cli.geopkg.GeopkgCommandProxy;
import org.locationtech.geogig.geotools.cli.postgis.PGCommandProxy;
import org.locationtech.geogig.geotools.cli.shp.ShpCommandProxy;

import com.google.inject.Binder;

/**
 * Provides bindings for GeoTools command extensions to the GeoGig command line interface.
 * 
 * @see PGCommandProxy
 * @see ShpCommandProxy
 * @see SQLServerCommandProxy
 * @see GeoJsonCommandProxy
 * @see GeopkgCommandProxy
 */

public class GeoToolsModule implements CLIModule {

    /**
     * @see CLIModule#configure(com.google.inject.Binder)
     */
    @Override
    public void configure(Binder binder) {
        binder.bind(PGCommandProxy.class);
        binder.bind(ShpCommandProxy.class);
        binder.bind(GeoJsonCommandProxy.class);
        binder.bind(GeopkgCommandProxy.class);
    }

}
