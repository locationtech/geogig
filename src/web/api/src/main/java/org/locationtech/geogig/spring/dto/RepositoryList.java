/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

import com.google.common.collect.Lists;

/**
 * Repository list response bean.
 */
@XmlRootElement(name = "repos")
@XmlAccessorType(XmlAccessType.FIELD)
public class RepositoryList extends LegacyResponse {

    @XmlElement(name = "repo")
    private List<RepositoryListRepo> repos;

    public List<RepositoryListRepo> getRepos() {
        return repos;
    }

    public RepositoryList setRepos(List<RepositoryListRepo> repos) {
        this.repos = repos;
        return this;
    }

    public RepositoryList addRepo(RepositoryListRepo repo) {
        if (repos == null) {
            repos = Lists.newArrayList();
        }
        repos.add(repo);
        return this;
    }

    @Override
    public void encodeInternal(StreamingWriter writer, MediaType format, String baseURL) {
        writer.writeStartElement("repos");
        //Iterator<String> repos = repoProvider.findRepositories();
        writer.writeStartArray("repo");
        if (repos != null) {
            for (RepositoryListRepo repo : repos) {
                String repoName = repo.getName();
                String repoId = repo.getId();
                writer.writeStartArrayElement("repo");
                if (repoId != null) {
                    writer.writeElement("id", repoId);
                }
                writer.writeElement("name", repoName);
                encodeAlternateAtomLink(writer, baseURL,
                        RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repoName, format);
                writer.writeEndArrayElement();
            }
        }
        writer.writeEndArray();
        writer.writeEndElement();
    }
}
