/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import jline.console.ConsoleReader;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;
import org.locationtech.geogig.api.porcelain.ResetOp;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

/**
 * Incorporates changes from the named commits (since the time their histories diverged from the
 * current branch) into the current branch.
 * <p>
 * CLI proxy for {@link MergeOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig merge [-m <msg>] [--ours] [--theirs] <commitish>...}
 * </ul>
 * 
 * @see MergeOp
 */
@Parameters(commandNames = "merge", commandDescription = "Merge two or more histories into one")
public class Merge extends AbstractCommand implements CLICommand {

    @Parameter(names = "-m", description = "Commit message")
    private String message;

    @Parameter(names = "--ours", description = "Use 'ours' strategy")
    private boolean ours;

    @Parameter(names = "--theirs", description = "Use 'theirs' strategy")
    private boolean theirs;

    @Parameter(names = "--no-commit", description = "Do not perform a commit after merging")
    private boolean noCommit;

    @Parameter(names = "--abort", description = "Aborts the current merge")
    private boolean abort;

    @Parameter(description = "<commitish>...")
    private List<String> commits = Lists.newArrayList();

    /**
     * Executes the merge command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(commits.size() > 0 || abort, "No commits provided to merge.");

        ConsoleReader console = cli.getConsole();

        final GeoGIG geogig = cli.getGeogig();

        Ansi ansi = newAnsi(console.getTerminal());

        if (abort) {
            Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
            if (!ref.isPresent()) {
                throw new CommandFailedException("There is no merge to abort <ORIG_HEAD missing>.");
            }

            geogig.command(ResetOp.class).setMode(ResetMode.HARD)
                    .setCommit(Suppliers.ofInstance(ref.get().getObjectId())).call();
            console.println("Merge aborted successfully.");
            return;
        }

        RevCommit commit;
        try {
            MergeOp merge = geogig.command(MergeOp.class);
            merge.setOurs(ours).setTheirs(theirs).setNoCommit(noCommit);
            merge.setMessage(message).setProgressListener(cli.getProgressListener());
            for (String commitish : commits) {
                Optional<ObjectId> commitId;
                commitId = geogig.command(RevParse.class).setRefSpec(commitish).call();
                checkParameter(commitId.isPresent(), "Commit not found '%s'", commitish);
                merge.addCommit(Suppliers.ofInstance(commitId.get()));
            }
            MergeReport report = merge.call();
            commit = report.getMergeCommit();
        } catch (RuntimeException e) {
            if (e instanceof NothingToCommitException || e instanceof IllegalArgumentException
                    || e instanceof IllegalStateException) {
                throw new CommandFailedException(e.getMessage(), e);
            }
            throw e;
        }
        final ObjectId parentId = commit.parentN(0).or(ObjectId.NULL);

        console.println("[" + commit.getId() + "] " + commit.getMessage());

        console.print("Committed, counting objects...");
        Iterator<DiffEntry> diff = geogig.command(DiffOp.class).setOldVersion(parentId)
                .setNewVersion(commit.getId()).call();

        int adds = 0, deletes = 0, changes = 0;
        DiffEntry diffEntry;
        while (diff.hasNext()) {
            diffEntry = diff.next();
            switch (diffEntry.changeType()) {
            case ADDED:
                ++adds;
                break;
            case REMOVED:
                ++deletes;
                break;
            case MODIFIED:
                ++changes;
                break;
            }
        }

        ansi.fg(Color.GREEN).a(adds).reset().a(" features added, ").fg(Color.YELLOW).a(changes)
                .reset().a(" changed, ").fg(Color.RED).a(deletes).reset().a(" deleted.").reset()
                .newline();

        console.print(ansi.toString());
    }
}
