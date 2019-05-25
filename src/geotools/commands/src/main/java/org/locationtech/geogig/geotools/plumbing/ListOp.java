/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.geotools.data.DataStore;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

/**
 * Internal operation for listing tables from a GeoTools {@link DataStore}.
 * 
 * @see DataStore
 */
public class ListOp extends AbstractGeoGigOp<Optional<List<String>>> {

    private DataStore dataStore;

    /**
     * Executes the list operation on the provided data store.
     * 
     * @return a list of all tables, or Optional.empty() if none were found
     */
    @Override
    protected Optional<List<String>> _call() {
        if (dataStore == null) {
            throw new GeoToolsOpException(StatusCode.DATASTORE_NOT_DEFINED);
        }
        List<String> typeNames;
        try {
            typeNames = dataStore.getNames().stream()
                    .map(org.opengis.feature.type.Name::getLocalPart).sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new GeoToolsOpException(StatusCode.UNABLE_TO_GET_NAMES);
        }

        return Optional.ofNullable(typeNames.isEmpty() ? null : typeNames);
    }

    /**
     * @param dataStore the data store to use for the import process
     * @return {@code this}
     */
    public ListOp setDataStore(DataStore dataStore) {
        this.dataStore = dataStore;
        return this;
    }

}
