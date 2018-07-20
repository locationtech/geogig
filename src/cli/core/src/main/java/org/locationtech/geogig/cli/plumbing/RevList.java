/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.geotools.util.Range;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.plumbing.ParseTimestamp;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Shows list of commits.
 * 
 * @see org.locationtech.geogig.porcelain.LogOp
 */
@ReadOnly
@Parameters(commandNames = "rev-list", commandDescription = "Show list of commits")
public class RevList extends AbstractCommand implements CLICommand {

    @ParametersDelegate
    public final RevListArgs args = new RevListArgs();

    private GeoGIG geogig;

    private Console console;

    /**
     * Executes the revlist command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!args.commits.isEmpty(), "No starting commit provided");

        geogig = cli.getGeogig();

        LogOp op = geogig.command(LogOp.class).setTopoOrder(args.topo)
                .setFirstParentOnly(args.firstParent);

        for (String commit : args.commits) {
            if (commit.contains("..")) {
                checkParameter(args.commits.size() == 1,
                        "Only one value accepted when using <since>..<until> syntax");
                List<String> sinceUntil = ImmutableList.copyOf((Splitter.on("..").split(commit)));
                checkParameter(sinceUntil.size() == 2 || sinceUntil.size() == 1,
                        "Invalid refSpec format, expected [<commit> ...]|[<since>..<until>]: %s",
                        commit);
                String sinceRefSpec;
                String untilRefSpec;
                if (sinceUntil.size() == 1) {
                    // just until was given
                    sinceRefSpec = null;
                    untilRefSpec = sinceUntil.get(0);
                } else {
                    sinceRefSpec = sinceUntil.get(0);
                    untilRefSpec = sinceUntil.get(1);
                }
                if (sinceRefSpec != null) {
                    Optional<ObjectId> since;
                    since = geogig.command(RevParse.class).setRefSpec(sinceRefSpec).call();
                    checkParameter(since.isPresent(), "Object not found '%s'", sinceRefSpec);
                    op.setSince(since.get());
                }
                if (untilRefSpec != null) {
                    Optional<ObjectId> until;
                    until = geogig.command(RevParse.class).setRefSpec(untilRefSpec).call();
                    checkParameter(until.isPresent(), "Object not found '%s'", sinceRefSpec);
                    op.setUntil(until.get());
                }
            } else {
                Optional<ObjectId> commitId = geogig.command(RevParse.class).setRefSpec(commit)
                        .call();
                checkParameter(commitId.isPresent(), "Object not found '%s'", commit);
                checkParameter(geogig.getRepository().commitExists(commitId.get()),
                        "%s does not resolve to a commit", commit);
                op.addCommit(commitId.get());
            }
        }
        if (args.author != null && !args.author.isEmpty()) {
            op.setAuthor(args.author);
        }
        if (args.committer != null && !args.committer.isEmpty()) {
            op.setCommiter(args.committer);
        }
        if (args.skip != null) {
            op.setSkip(args.skip.intValue());
        }
        if (args.limit != null) {
            op.setLimit(args.limit.intValue());
        }
        if (args.since != null || args.until != null) {
            Date since = new Date(0);
            Date until = new Date();
            if (args.since != null) {
                since = new Date(geogig.command(ParseTimestamp.class).setString(args.since).call());
            }
            if (args.until != null) {
                until = new Date(geogig.command(ParseTimestamp.class).setString(args.until).call());
            }
            op.setTimeRange(new Range<Date>(Date.class, since, until));
        }
        if (!args.pathNames.isEmpty()) {
            for (String s : args.pathNames) {
                op.addPath(s);
            }
        }
        Iterator<RevCommit> log = op.call();
        console = cli.getConsole();

        RawPrinter printer = new RawPrinter(args.changed);
        while (log.hasNext()) {
            printer.print(log.next());
            console.flush();
        }
    }

    private class RawPrinter {

        private boolean showChanges;

        public RawPrinter(boolean showChanges) {
            this.showChanges = showChanges;
        }

        public void print(RevCommit commit) throws IOException {

            StringBuilder sb = new StringBuilder();
            sb.append("commit ").append(commit.getId().toString()).append('\n');
            sb.append("tree ").append(commit.getTreeId().toString()).append('\n');
            sb.append("parent");
            for (ObjectId parentId : commit.getParentIds()) {
                sb.append(' ').append(parentId.toString());
            }
            sb.append('\n');
            sb.append("author ").append(format(commit.getAuthor())).append('\n');
            sb.append("committer ").append(format(commit.getCommitter())).append('\n');

            if (commit.getMessage() != null) {
                sb.append("message\n");
                sb.append("\t" + commit.getMessage().replace("\n", "\n\t"));
                sb.append('\n');
            }
            if (showChanges) {
                try (AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                        .setOldVersion(commit.parentN(0).or(ObjectId.NULL))
                        .setNewVersion(commit.getId()).call()) {
                    DiffEntry diffEntry;
                    sb.append("changes\n");
                    while (diff.hasNext()) {
                        diffEntry = diff.next();
                        String path = diffEntry.newPath() != null ? diffEntry.newPath()
                                : diffEntry.oldPath();
                        sb.append('\t').append(path).append(' ')
                                .append(diffEntry.oldObjectId().toString()).append(' ')
                                .append(diffEntry.newObjectId().toString()).append('\n');
                    }
                }
            }
            console.println(sb.toString());
        }

        private String format(RevPerson p) {
            StringBuilder sb = new StringBuilder();
            sb.append(p.getName().or("[unknown]")).append(' ');
            sb.append(p.getEmail().or("[unknown]")).append(' ');
            sb.append(p.getTimestamp()).append(' ').append(p.getTimeZoneOffset());
            return sb.toString();
        }

    }

}
