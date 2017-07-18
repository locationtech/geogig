/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dao;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.util.Iterator;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.AtomLink;
import org.locationtech.geogig.spring.dto.RepositoryList;
import org.locationtech.geogig.spring.dto.RepositoryListRepo;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;

/**
 * Data Access Object for obtaining instances of {@link RepositoryList}s.
 */
@Repository("repositoryListDAO")
public class RepositoryListDAO {

    public RepositoryList getRepositoryList(RepositoryProvider provider, MediaType type,
            String basUrl) {
        RepositoryList list = new RepositoryList();
        Iterator<String> repos = provider.findRepositories();
        while (repos.hasNext()) {
            final String repoName = repos.next();
            RepositoryListRepo listRepo = getRepositoryListRepo(repoName, type, basUrl);
            list.addRepo(listRepo);
        }
        return list;
    }

    private RepositoryListRepo getRepositoryListRepo(String repoName, MediaType type,
            String baseUrl) {
        RepositoryListRepo repo = new RepositoryListRepo();
        repo.setName(repoName);
        AtomLink link = new AtomLink();
        // set the Type on the AtomLink
        String hrefExt;
        switch (type.getSubtype()) {
            case "json":
                link.setType(APPLICATION_JSON_VALUE);
                hrefExt = "json";
                break;
            default:
                link.setType(APPLICATION_XML_VALUE);
                hrefExt = "xml";
        }
        // build the href
        link.setHref(baseUrl + "/" + repoName + "." + hrefExt);
        repo.setLink(link);
        return repo;
    }
}
