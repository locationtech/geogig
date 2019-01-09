/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import org.locationtech.geogig.model.PriorityService;
import org.locationtech.geogig.model.ServiceFinder;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.NonNull;

public interface DAGStorageProviderFactory extends PriorityService {

    public static final String ENV_VARIABLE = "TEMP_STORAGE_FACTORY";

    public DAGStorageProvider newInstance(@NonNull ObjectStore treeStore);

    public static DAGStorageProviderFactory defaultInstance() {
        return new ServiceFinder().environmentVariable(ENV_VARIABLE).systemProperty(ENV_VARIABLE)
                .lookupDefaultService(DAGStorageProviderFactory.class);
    }
}
