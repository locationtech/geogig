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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.MultiValueMapParams;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.spring.dto.RepositoryInfo;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;

/**
 * Controller for repository commands and repository info.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryCommandController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryCommandController.class);

    @GetMapping
    public RepositoryInfo getRepositoryInfo(@PathVariable String repoName) {
        return new RepositoryInfo().setName(repoName);
    }

    @RequestMapping(value = "/{command}", method = { RequestMethod.GET, RequestMethod.PUT,
            RequestMethod.POST, RequestMethod.DELETE })
    public void runCommand(@PathVariable String repoName, @PathVariable String command,
            @RequestParam MultiValueMap<String, String> params,
            HttpServletRequest request, HttpServletResponse response) {
        AbstractWebAPICommand webCommand = buildCommand(command);
        RequestMethod method = RequestMethod.valueOf(request.getMethod());
        webCommand.setParameters(new MultiValueMapParams(params));
        SpringContext context = buildContext(request, repoName, webCommand);
        if (webCommand.supports(method)) {
            webCommand.run(context);
            encode(context.getResponseContent(), request, response);
        } else {
            encodeCommandResponse(false, new LegacyResponse() {
                @Override
                public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
                    writer.writeElement("error", "unsupported method.");
                }
            }, request, response);
        }
    }

    protected AbstractWebAPICommand buildCommand(String commandName) {
        return CommandBuilder.build(commandName);
    }

    private SpringContext buildContext(HttpServletRequest request, String repoName,
            AbstractWebAPICommand webCommand) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        RepositoryProvider provider = null;
        Repository repository = null;
        if (repoProvider.isPresent()) {
            provider = repoProvider.get();
            Optional<Repository> repo = provider.getGeogig(repoName);
            if (repo.isPresent() && repo.get().isOpen()) {
                repository = repo.get();
            }
        }
        return new SpringContext(provider, repository, request);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    static class SpringContext implements CommandContext {

        CommandResponse responseContent = null;

        final Repository repository;

        final HttpServletRequest request;

        final RepositoryProvider provider;

        SpringContext(RepositoryProvider provider, Repository repository,
                HttpServletRequest request) {
            this.provider = provider;
            this.repository = repository;
            this.request = request;
        }

        @Override
        public Repository getRepository() {
            return repository;
        }

        @Override
        public RequestMethod getMethod() {
            return RequestMethod.valueOf(request.getMethod());
        }

        @Override
        public void setResponseContent(CommandResponse responseContent) {
            this.responseContent = responseContent;
        }

        public CommandResponse getResponseContent() {
            return this.responseContent;
        }

        @Override
        public String getBaseURL() {
            return request.getRequestURI();
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            // TODO Auto-generated method stub

        }

        @Override
        public RepositoryProvider getRepositoryProvider() {
            return provider;
        }

    }
}
