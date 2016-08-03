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

import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp.DataStoreSupplier;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.ParameterSet;

/**
 * Interface for DataStore imports.
 *
 * @see org.locationtech.geogig.geotools.plumbing.DataStoreImportOp
 */
public interface DataStoreImportContextService {

    /**
     * Indicates if this ImportContext is applicable to the provided format.
     *
     * @param format String representation of the desired import context format.
     *
     * @return true if this ImportContext can handle the supplied format, false otherwise.
     */
    public boolean accepts(String format);

    /**
     * Builds a DataStore from the request parameters, from which data should be imported.
     *
     * @param options Set of request parameters.
     *
     * @return A DataStore representation of the data for import.
     */
    public DataStoreSupplier getDataStore(ParameterSet options);

    /**
     * Retrieves a custom command description.
     *
     * @return An appropriate command description.
     */
    public String getCommandDescription();

    /**
     * Retrieves the {@link DataStoreImportOp} implementation to use for the import.
     * 
     * @param context the command context to use.
     * @param options the options for the command
     * 
     * @return the import command to use.
     */
    public DataStoreImportOp<?> createCommand(final Context context,
            final ParameterSet options);
}
