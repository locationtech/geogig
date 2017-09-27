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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;

import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Allows a user to rename a repository.
 */

public class RenameRepository extends AbstractWebAPICommand {
    String name;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setName(options.getRequiredValue("name"));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.POST.equals(method);
    }

    /**
     * Mutator for the name variable
     * 
     * @param name - the new name of the repo
     */
    public void setName(String name) {
        this.name = name;
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
        final Context geogig = this.getRepositoryContext(context);

        String oldRepoName = geogig.command(ResolveRepositoryName.class).call();
        checkArgument(!name.equals(oldRepoName),
                "New name must be different than the existing one.");

        Iterator<String> existingRepos = context.getRepositoryProvider().findRepositories();

        while (existingRepos.hasNext()) {
            checkArgument(!name.equals(existingRepos.next()),
                    "A repository with that name already exists.");
        }
        
        geogig.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("repo.name")
                .setScope(ConfigScope.LOCAL).setValue(name).call();

        final String repositoryName = name;

        context.getRepositoryProvider().invalidate(oldRepoName);

        context.setResponseContent(new CommandResponse() {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.MOVED_PERMANENTLY;
            }

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRepoInitResponse(repositoryName, context.getBaseURL(),
                        RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repositoryName);
                out.finish();
            }
        });
    }
}
