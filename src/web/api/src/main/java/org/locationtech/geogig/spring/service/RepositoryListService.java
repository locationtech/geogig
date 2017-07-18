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

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.RepositoryList;
import org.locationtech.geogig.spring.dao.RepositoryListDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Internal service for constructing a {@link RepositoryList} DTO for Controllers to consume.
 */
@Service("repositoryListService")
public class RepositoryListService {

    @Autowired
    private RepositoryListDAO repositoryListDAO;

    public RepositoryList getRepositoryList(RepositoryProvider provider, MediaType type,
            String basUrl) {
        return repositoryListDAO.getRepositoryList(provider, type, basUrl);
    }
}
