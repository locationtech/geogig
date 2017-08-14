/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.net.URI;
import java.util.Map;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.RepositoryInitRepo;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Internal service for initializing a repository.
 */
@Service("repositoryInitService")
public class RepositoryInitService {

    public RepositoryInitRepo initRepository(RepositoryProvider provider, String repositoryName,
            Map<String, String> parameters) throws RepositoryConnectionException {
        Optional<Repository> repo = provider.getGeogig(repositoryName);
        if (repo.isPresent() && repo.get().isOpen()) {
            throw new CommandSpecException("Cannot run init on an already initialized repository.",
                    HttpStatus.CONFLICT);
        }

        final Repository newRepo = provider.createGeogig(repositoryName, parameters);

        InitOp command = newRepo.command(InitOp.class);

        command.call();

        Optional<URI> repoUri = newRepo.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(),
                "Unable to resolve URI of newly created repository.");

        final String repoName = RepositoryResolver.load(repoUri.get())
                .command(ResolveRepositoryName.class).call();
        RepositoryInitRepo info = new RepositoryInitRepo();
        info.setName(repoName);
        info.setLink(repoUri.get().toString());
        return info;
    }
}
