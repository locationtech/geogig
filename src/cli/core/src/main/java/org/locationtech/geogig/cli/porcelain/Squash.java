/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.SquashOp;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Squashes a set of commits into a single one.
 * <p>
 * CLI proxy for {@link org.locationtech.geogig.porcelain.SquashOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig squash [<message>] <since_commit> <until_commit>
 * </ul>
 * 
 * @see org.locationtech.geogig.porcelain.LogOp
 */
@Parameters(commandNames = "squash", commandDescription = "Squash commits")
public class Squash extends AbstractCommand implements CLICommand {

    @Parameter(description = "<since_commit> <until_commit>", arity = 2)
    private List<String> commits = Lists.newArrayList();

    @Parameter(names = "-m", description = "Commit message")
    private String message;

    /**
     * Executes the squash command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) {
        checkParameter(commits.size() == 2, "2 commit references must be supplied");

        final GeoGIG geogig = cli.getGeogig();

        Optional<ObjectId> sinceId = geogig.command(RevParse.class).setRefSpec(commits.get(0))
                .call();
        checkParameter(sinceId.isPresent(), "'since' reference cannot be found");
        checkParameter(geogig.getRepository().commitExists(sinceId.get()),
                "'since' reference does not resolve to a commit");
        RevCommit sinceCommit = geogig.getRepository().getCommit(sinceId.get());

        Optional<ObjectId> untilId = geogig.command(RevParse.class).setRefSpec(commits.get(1))
                .call();
        checkParameter(untilId.isPresent(), "'until' reference cannot be found");
        checkParameter(geogig.getRepository().commitExists(untilId.get()),
                "'until' reference does not resolve to a commit");
        RevCommit untilCommit = geogig.getRepository().getCommit(untilId.get());

        geogig.command(SquashOp.class).setSince(sinceCommit).setUntil(untilCommit)
                .setMessage(message).call();
    }

}
