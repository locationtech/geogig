/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;

import com.google.common.base.Optional;

/**
 * Common Service utilities.
 */
public abstract class AbstractRepositoryService {

    public Repository getRepository(RepositoryProvider provider, String repoName) {
        Optional<Repository> geogig = provider.getGeogig(repoName);
        return geogig.orNull();
    }

}
