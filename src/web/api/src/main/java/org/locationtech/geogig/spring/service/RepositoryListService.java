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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.util.Iterator;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.AtomLink;
import org.locationtech.geogig.spring.dto.RepositoryInfo;
import org.locationtech.geogig.spring.dto.RepositoryList;
import org.locationtech.geogig.spring.dto.RepositoryListRepo;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;

/**
 * Internal service for constructing a {@link RepositoryList} DTO for Controllers to consume.
 */
@Service("repositoryListService")
public class RepositoryListService {

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

    public RepositoryInfo getRepository(RepositoryProvider provider, MediaType type, String baseUrl,
            String repoName) {
        Optional<Repository> geogig = provider.getGeogig(repoName);
        if (geogig.isPresent()) {
            Repository repo = geogig.get();
            RepositoryInfo repoInfo = new RepositoryInfo().setName(repoName).
                    setLocation(repo.getLocation().toString());
            // TODO: need to set the ID, if it exists
            return repoInfo;
        }
        return null;
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

    private Object RepositoryInfo() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
