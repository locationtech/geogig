/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GeoGIG;

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
 * <li>{@code geogig merge [-m <msg>] [--ours] [--theirs] <commitish>...}
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

    @Parameter(names = "--no-ff", description = "Create a merge commit even when the merge resolves as fast forward")
    private boolean noFastForward;

    @Parameter(names = "--ff-only", description = "Refuse to merge unless the current HEAD is already up-to-date or the merge can be resolved as a fast forward")
    private boolean fastForwardOnly;

    @Parameter(names = "--abort", description = "Aborts the current merge")
    private boolean abort;

    @Parameter(description = "<commitish>...")
    private List<String> commits = Lists.newArrayList();

    @Parameter(names = { "--quiet",
            "-q" }, description = "Do not count and report changes. Useful to avoid unnecessary waits on large changesets")
    private boolean quiet;

    /**
     * Executes the merge command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(commits.size() > 0 || abort, "No commits provided to merge.");

        Console console = cli.getConsole();

        final GeoGIG geogig = cli.getGeogig();

        if (abort) {
            Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
            if (!ref.isPresent()) {
                throw new CommandFailedException("There is no merge to abort <ORIG_HEAD missing>.",
                        true);
            }

            geogig.command(ResetOp.class).setMode(ResetMode.HARD)
                    .setCommit(Suppliers.ofInstance(ref.get().getObjectId())).call();
            console.println("Merge aborted successfully.");
            return;
        }

        final ProgressListener progress = cli.getProgressListener();
        RevCommit commit;
        try {
            MergeOp merge = geogig.command(MergeOp.class);
            merge.setOurs(ours).setTheirs(theirs).setNoCommit(noCommit);
            merge.setMessage(message).setProgressListener(progress);
            merge.setFastForwardOnly(fastForwardOnly).setNoFastForward(noFastForward);
            for (String commitish : commits) {
                Optional<ObjectId> commitId;
                commitId = geogig.command(RevParse.class).setRefSpec(commitish).call();
                checkParameter(commitId.isPresent(), "Commit not found '%s'", commitish);
                merge.addCommit(commitId.get());
            }
            MergeReport report = merge.call();
            if (progress.isCanceled()) {
                return;
            }
            commit = report.getMergeCommit();
        } catch (RuntimeException e) {
            if (e instanceof NothingToCommitException || e instanceof IllegalArgumentException
                    || e instanceof IllegalStateException || e instanceof ConflictsException) {
                throw new InvalidParameterException(e.getMessage(), e);
            }
            throw e;
        }
        final ObjectId parentId = commit.parentN(0).or(ObjectId.NULL);

        console.println("[" + commit.getId() + "] " + commit.getMessage());

        if (progress.isCanceled()) {
            return;
        }

        if (!quiet) {
            console.print("Committed, counting objects...");
            console.flush();
            final DiffObjectCount diffCount = geogig.command(DiffCount.class)
                    .setOldVersion(parentId.toString()).setNewTree(commit.getTreeId())
                    .setProgressListener(progress).call();

            Ansi ansi = newAnsi(console);
            ansi.fg(Color.GREEN).a(diffCount.getFeaturesAdded()).reset().a(" features added, ")
                    .fg(Color.YELLOW).a(diffCount.getFeaturesChanged()).reset().a(" changed, ")
                    .fg(Color.RED).a(diffCount.getFeaturesRemoved()).reset().a(" deleted.").reset()
                    .newline();

            console.print(ansi.toString());
        }

    }
}
