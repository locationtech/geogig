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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.SquashOp;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Squashes a set of commits into a single one.
 * <p>
 * CLI proxy for {@link org.locationtech.geogig.porcelain.SquashOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig squash [<message>] <since_commit> <until_commit>
 * 
</ul>
 * 
 * @see org.locationtech.geogig.porcelain.LogOp
 */
@Command(name = "squash", description = "Squash commits")
public class Squash extends AbstractCommand implements CLICommand {

    @Parameters(description = "<since_commit> <until_commit>")
    private List<String> commits = new ArrayList<>();

    @Option(names = "-m", description = "Commit message")
    private String message;

    /**
     * Executes the squash command using the provided options.
     */
    public @Override void runInternal(GeogigCLI cli) {
        checkParameter(commits.size() == 2, "2 commit references must be supplied");

        final Geogig geogig = cli.geogig();

        Optional<ObjectId> sinceId = geogig.commands().command(RevParse.class)
                .setRefSpec(commits.get(0)).call();
        checkParameter(sinceId.isPresent(), "'since' reference cannot be found");
        checkParameter(geogig.objects().commitExists(sinceId.get()),
                "'since' reference does not resolve to a commit");
        RevCommit sinceCommit = geogig.objects().getCommit(sinceId.get());

        Optional<ObjectId> untilId = geogig.commands().command(RevParse.class)
                .setRefSpec(commits.get(1)).call();
        checkParameter(untilId.isPresent(), "'until' reference cannot be found");
        checkParameter(geogig.objects().commitExists(untilId.get()),
                "'until' reference does not resolve to a commit");
        RevCommit untilCommit = geogig.objects().getCommit(untilId.get());

        geogig.commands().command(SquashOp.class).setSince(sinceCommit).setUntil(untilCommit)
                .setMessage(message).call();
    }

}
