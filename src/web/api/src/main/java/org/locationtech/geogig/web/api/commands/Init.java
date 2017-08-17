/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.net.URI;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * The interface for the Init operation in GeoGig.
 * 
 * Web interface for {@link InitOp}
 */

public class Init extends AbstractWebAPICommand {

    @Override
    protected void setParametersInternal(ParameterSet options) {
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.PUT.equals(method);
    }

    @Override
    public boolean requiresOpenRepo() {
        return false;
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        Repository repository = context.getRepository();
        if (repository != null && repository.isOpen()) {
            throw new CommandSpecException("Cannot run init on an already initialized repository.",
                    HttpStatus.CONFLICT);
        }

        final Context geogig = this.getRepositoryContext(context);

        InitOp command = geogig.command(InitOp.class);

        command.call();

        try {
            Optional<URI> repoUri = geogig.command(ResolveGeogigURI.class).call();
            Preconditions.checkState(repoUri.isPresent(),
                    "Unable to resolve URI of newly created repository.");

            final String repositoryName = RepositoryResolver.load(repoUri.get())
                    .command(ResolveRepositoryName.class).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeRepoInitResponse(repositoryName, context.getBaseURL(),
                            RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repositoryName);
                    out.finish();
                }
            });
            setStatus(HttpStatus.CREATED);
        } catch (RepositoryConnectionException e) {
            throw new CommandSpecException(
                    "Repository was created, but was unable to connect to it immediately.");
        }
    }

}
