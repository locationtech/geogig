/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.porcelain;

import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.CherryPickOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

/**
 * Given an existing commit, apply the change it introduces, recording a new commit . This requires
 * your working tree to be clean (no modifications from the HEAD commit).
 * <p>
 * CLI proxy for {@link CherryPickOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig cherry-pick <commitish>}
 * </ul>
 * 
 * @see CherryPickOp
 */
@Parameters(commandNames = "cherry-pick", commandDescription = "Apply the changes introduced by existing commits")
public class CherryPick extends AbstractCommand implements CLICommand {

    @Parameter(description = "<commitish>...")
    private List<String> commits = Lists.newArrayList();

    @Override
    public void runInternal(GeogigCLI cli) {
        final GeoGIG geogig = cli.getGeogig();
        checkParameter(commits.size() > 0, "No commits specified.");
        checkParameter(commits.size() < 2, "Too many commits specified.");

        CherryPickOp cherryPick = geogig.command(CherryPickOp.class);

        Optional<ObjectId> commitId;
        commitId = geogig.command(RevParse.class).setRefSpec(commits.get(0)).call();
        checkParameter(commitId.isPresent(), "Commit not found '%s'", commits.get(0));
        cherryPick.setCommit(Suppliers.ofInstance(commitId.get()));

        cherryPick.call();

    }
}
