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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.ParameterSetFactory;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.UploadCommandResource;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.spring.service.RepositoryService;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.StreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Optional;

/**
 * Controller for repository commands and repository info.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryCommandController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryCommandController.class);

    @Autowired
    private RepositoryService repositoryService;

    @GetMapping
    public void getRepositoryInfo(@PathVariable String repoName,
            final HttpServletRequest request, HttpServletResponse response) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            encode(repositoryService.getRepositoryInfo(repoProvider.get(), repoName),
                    request, response);
        } else {
            throw NO_PROVIDER;
        }
    }

    @DeleteMapping
    public void deleteRepository(@PathVariable String repoName,
            final HttpServletRequest request, HttpServletResponse response) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            RepositoryProvider provider = repoProvider.get();
            if (!provider.hasGeoGig(repoName)) {
                throw new CommandSpecException("error:No repository to delete.",
                        HttpStatus.NOT_FOUND);
            }
            // TODO: Handle deletes with transaction ID
            throw new RuntimeException("IMPLEMENT DELETE");
        } else {
            throw new CommandSpecException("error:No repository to delete.",
                    HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Runs the given command.
     *
     * @param repoName the repository name
     * @param command the command name
     * @param params request parameters
     * @param file File to upload
     * @param request the request
     * @param response the response object
     * @param entity the RequestEntity payload, if any
     * @throws IOException
     */
    @RequestMapping(value = "/{command}")
    public void runCommand(@PathVariable String repoName, @PathVariable String command,
            @RequestParam MultiValueMap<String, String> params,
            @RequestParam(required = false, name = UploadCommandResource.UPLOAD_FILE_KEY) MultipartFile file,
            HttpServletRequest request, HttpServletResponse response, RequestEntity<String> entity)
            throws IOException {
        AbstractWebAPICommand webCommand = buildCommand(command);
        RequestMethod method = RequestMethod.valueOf(request.getMethod());
        File uploadedFile = null;
        if (file != null) {
            uploadedFile = File.createTempFile(
                    "geogig-" + UploadCommandResource.UPLOAD_FILE_KEY + "-", ".tmp");
            uploadedFile.deleteOnExit();
            file.transferTo(uploadedFile);
        }
        if (webCommand.supports(method)) {
            // build the PArameterSet from the request entity and parameters
            webCommand.setParameters(ParameterSet.concat(getParamsFromEntity(entity),
                    ParameterSetFactory.buildParameterSet(params, uploadedFile)));
            SpringContext context = buildContext(request, repoName, webCommand);
            webCommand.run(context);
            encode(context.getResponseContent(), request, response);
        } else {
            // determined allowed set of methods
            HashSet<String> allowedMethods = new HashSet<>(4);
            if (webCommand.supports(RequestMethod.GET)) {
                allowedMethods.add(RequestMethod.GET.toString());
            }
            if (webCommand.supports(RequestMethod.POST)) {
                allowedMethods.add(RequestMethod.POST.toString());
            }
            if (webCommand.supports(RequestMethod.PUT)) {
                allowedMethods.add(RequestMethod.PUT.toString());
            }
            if (webCommand.supports(RequestMethod.DELETE)) {
                allowedMethods.add(RequestMethod.DELETE.toString());
            }
            throw new CommandSpecException("The request method is unsupported for this operation.",
                    HttpStatus.METHOD_NOT_ALLOWED, allowedMethods);
        }
    }

    /**
     * Build an {@link AbstractWebAPICommand} from a command name.
     *
     * @param commandName the name of the command
     * @return
     */
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

        LegacyResponse responseContent = null;

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
        public void setResponseContent(LegacyResponse responseContent) {
            this.responseContent = responseContent;
        }

        public LegacyResponse getResponseContent() {
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
