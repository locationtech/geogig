/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.CherryPickOp;
import org.locationtech.geogig.porcelain.ConflictsException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Given an existing commit, apply the change it introduces, recording a new commit . This requires
 * your working tree to be clean (no modifications from the HEAD commit).
 * <p>
 * CLI proxy for {@link CherryPickOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig cherry-pick <commitish>}
 * </ul>
 * 
 * @see CherryPickOp
 */
@Command(name = "cherry-pick", aliases = "chp", description = "Apply the changes introduced by existing commits")
public class CherryPick extends AbstractCommand implements CLICommand {

    @Parameters(description = "refspec resolving to the commit to cherry pick")
    private List<String> commits = new ArrayList<>();

    public @Override void runInternal(GeogigCLI cli) {
        final Geogig geogig = cli.getGeogig();
        checkParameter(commits.size() > 0, "No commits specified.");
        checkParameter(commits.size() < 2, "Too many commits specified.");

        CherryPickOp cherryPick = geogig.command(CherryPickOp.class);

        Optional<ObjectId> commitId;
        commitId = geogig.command(RevParse.class).setRefSpec(commits.get(0)).call();
        checkParameter(commitId.isPresent(), "Commit not found '%s'", commits.get(0));
        cherryPick.setCommit(commitId::get);

        try {
            cherryPick.call();
        } catch (ConflictsException e) {
            throw new CommandFailedException(e.getMessage(), true);
        }
    }
}
