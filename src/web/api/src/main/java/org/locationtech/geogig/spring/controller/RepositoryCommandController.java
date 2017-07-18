/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import org.locationtech.geogig.spring.dto.RepositoryInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for repository commands and repository info.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryCommandController {

    @GetMapping
    public RepositoryInfo getRepositoryInfo(@PathVariable(name = "repoName") String repoName) {
        return new RepositoryInfo().setName(repoName);
    }
}
