/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import static org.springframework.web.bind.annotation.RequestMethod.TRACE;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.ConsoleRunCommandResponse;
import org.locationtech.geogig.spring.service.LegacyConsoleService;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

/**
 *
 */
@Controller
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/repo/console")
public class ConsoleController extends AbstractRepositoryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleController.class);

    @Autowired
    private LegacyConsoleService legacyConsoleService;

    @RequestMapping(method = { PUT, DELETE, PATCH, TRACE, OPTIONS })
    public void catchAll() {
        // if we hit this controller, it's a 405
        supportedMethods(Sets.newHashSet(GET.toString(), POST.toString()));
    }

    @PostMapping(value = "/run-command")
    public void postCommand(@PathVariable(name = "repoName") String repoName,
            InputStream requestInput, HttpServletRequest request, HttpServletResponse response) {
        Optional<RepositoryProvider> optional = getRepoProvider(request);
        if (optional.isPresent()) {
            final RepositoryProvider provider = optional.get();
            // ensure the repo exists and is opened
            if (!isOpenRepository(provider, repoName, response)) {
                // done
                return;
            }
            // get the command response from the service
            ConsoleRunCommandResponse runCommandResponse = legacyConsoleService.runCommand(provider,
                    repoName, requestInput);
            // encode
            encode(runCommandResponse, request, response);
        } else {
            throw NO_PROVIDER;
        }
    }

    @GetMapping
    public void getConsoleNoSlash(@PathVariable(name = "repoName") String repoName,
            HttpServletRequest request, HttpServletResponse response) {
        ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromRequest(request);
        String location = builder.build().toString() + "/";

        response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
        response.setHeader("Location", location);
    }

    @GetMapping(value = "/", produces = "text/html")
    public String getConsole(@PathVariable(name = "repoName") String repoName,
            HttpServletRequest request, HttpServletResponse response) {
        // get the provider
        Optional<RepositoryProvider> optional = getRepoProvider(request);
        if (optional.isPresent()) {
            final RepositoryProvider provider = optional.get();
            // ensure the repo exists and is opened
            if (!isOpenRepository(provider, repoName, response)) {
                // done
                throw new CommandSpecException("Repository not found.", HttpStatus.NOT_FOUND);
            }
            return "terminal.html";
        } else {
            throw NO_PROVIDER;
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
