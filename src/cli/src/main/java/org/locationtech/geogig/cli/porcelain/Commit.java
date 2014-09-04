/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import jline.console.ConsoleReader;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.plumbing.ParseTimestamp;
import org.locationtech.geogig.api.plumbing.ResolveObjectType;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.ReadMergeCommitMessageOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 * Stores the current contents of the index in a new commit along with a log message from the user
 * describing the changes.
 * <p>
 * CLI proxy for {@link CommitOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig commit -m <msg>}
 * </ul>
 * 
 * @see CommitOp
 */
@Parameters(commandNames = "commit", commandDescription = "Record staged changes to the repository")
public class Commit extends AbstractCommand implements CLICommand {

    @Parameter(names = "-m", description = "Commit message")
    private String message;

    @Parameter(names = "-c", description = "Commit to reuse")
    private String commitToReuse;

    @Parameter(names = "-t", description = "Commit timestamp")
    private String commitTimestamp;

    @Parameter(names = "--amend", description = "Amends last commit")
    private boolean amend;

    @Parameter(description = "<pathFilter>  [<paths_to_commit]...")
    private List<String> pathFilters = Lists.newLinkedList();

    /**
     * Executes the commit command using the provided options.
     * 
     * @param cli
     * @see org.locationtech.geogig.cli.AbstractCommand#runInternal(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        final GeoGIG geogig = cli.getGeogig();

        if (message == null || Strings.isNullOrEmpty(message)) {
            message = geogig.command(ReadMergeCommitMessageOp.class).call();
        }
        checkParameter(!Strings.isNullOrEmpty(message) || commitToReuse != null || amend,
                "No commit message provided");

        ConsoleReader console = cli.getConsole();

        Ansi ansi = newAnsi(console.getTerminal());

        RevCommit commit;
        try {
            CommitOp commitOp = geogig.command(CommitOp.class).setMessage(message).setAmend(amend);
            if (commitTimestamp != null && !Strings.isNullOrEmpty(commitTimestamp)) {
                Long millis = geogig.command(ParseTimestamp.class).setString(commitTimestamp)
                        .call();
                commitOp.setCommitterTimestamp(millis.longValue());
            }

            if (commitToReuse != null) {
                Optional<ObjectId> commitId = geogig.command(RevParse.class)
                        .setRefSpec(commitToReuse).call();
                checkParameter(commitId.isPresent(), "Provided reference does not exist");
                TYPE type = geogig.command(ResolveObjectType.class).setObjectId(commitId.get())
                        .call();
                checkParameter(TYPE.COMMIT.equals(type),
                        "Provided reference does not resolve to a commit");
                commitOp.setCommit(geogig.getRepository().getCommit(commitId.get()));
            }
            commit = commitOp.setPathFilters(pathFilters)
                    .setProgressListener(cli.getProgressListener()).call();
        } catch (NothingToCommitException noChanges) {
            throw new CommandFailedException(noChanges.getMessage(), noChanges);
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
