/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.postgis;

import org.geotools.data.DataStore;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.DataStoreExport;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports features from a feature type into a PostGIS database.
 * 
 * @see ExportOp
 */
@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to PostGIS")
public class PGExport extends DataStoreExport implements CLICommand {

    /**
     * Common arguments for PostGIS commands.
     */
    @ParametersDelegate
    public PGCommonArgs commonArgs = new PGCommonArgs();

    final PGSupport support = new PGSupport();

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    /**
     * Transforms all features to use a feature id that is compatible with postgres.
     * 
     * @param featureType the feature type of the features to transform
     * @return the transforming function
     */
    @Override
    protected Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType) {
        Function<Feature, Optional<Feature>> function = (feature) -> {
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            builder.init((SimpleFeature) feature);
            String fid = feature.getIdentifier().getID();
            String fidPrefix = feature.getType().getName().getLocalPart();
            if (fid.startsWith(fidPrefix)) {
                fid = fid.substring(fidPrefix.length() + 1);
            }
            builder.featureUserData(Hints.PROVIDED_FID, fid);
            Feature modifiedFeature = builder.buildFeature(fid);
            return Optional.fromNullable(modifiedFeature);
        };

        return function;
    }
}
