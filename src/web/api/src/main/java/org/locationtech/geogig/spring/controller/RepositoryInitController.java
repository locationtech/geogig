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
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import org.locationtech.geogig.spring.dto.InitRequest;
import org.locationtech.geogig.spring.dto.RepositoryInitRepo;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handing Repository INIT requests.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}/init",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryInitController {

    @PutMapping(consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    public RepositoryInitRepo initRepositoryFromJson(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody InitRequest request) {
        // parse the request
        System.out.println("Init Request JSON body: " + request);
        return new RepositoryInitRepo().setName(repoName);
    }

    @PutMapping(consumes = {APPLICATION_FORM_URLENCODED_VALUE})
    public RepositoryInitRepo initRepositoryFromForm(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody MultiValueMap<String, String> request) {
        // parse the request
        System.out.println("Init Request FORM body:");
        for (MultiValueMap.Entry entry : request.entrySet()) {
            System.out.println("\t" + entry.getKey() + " = " + entry.getValue());
        }
        return new RepositoryInitRepo().setName(repoName);
    }
}
