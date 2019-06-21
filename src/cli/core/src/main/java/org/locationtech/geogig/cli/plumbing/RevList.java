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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Shows list of commits.
 * 
 * @see org.locationtech.geogig.porcelain.LogOp
 */
@ReadOnly
@Command(name = "rev-list", description = "Show list of commits")
public class RevList extends AbstractCommand implements CLICommand {

    /**
     * The commits to use for starting the list of output commits
     * 
     */
    @Parameters(arity = "1..*", description = "< [<commit> ...]|[<since>..<until>]")
    public List<String> commits = new ArrayList<String>();

    @Option(names = { "--max", "-n" }, description = "Maximum number of commits to log.")
    public Integer limit;

    @Option(names = "--skip", description = "Skip number commits before starting to show the commit output.")
    public Integer skip;

    @Option(names = "--since", description = "Maximum number of commits to log")
    public String since;

    @Option(names = "--until", description = "Maximum number of commits to log")
    public String until;

    @Option(names = "--author", description = "Return only commits by authors with names maching the passed regular expression")
    public String author;

    @Option(names = "--committer", description = "Return only commits by committer with names maching the passed regular expression")
    public String committer;

    @Option(names = { "--path",
            "-p" }, description = "Print only commits that have modified the given path(s)")
    public List<String> pathNames = new ArrayList<>();

    @Option(names = "--summary", description = "Show summary of changes for each commit")
    public boolean summary;

    @Option(names = "--topo-order", description = "Avoid showing commits on multiple lines of history intermixed")
    public boolean topo;

    @Option(names = "--first-parent", description = "Use only the first parent of each commit, showing a linear history")
    public boolean firstParent;

    @Option(names = "--changed", description = "Show paths affected by each commit")
    public boolean changed;

    private GeoGIG geogig;

    private Console console;

    /**
     * Executes the revlist command using the provided options.
     */
    public @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!this.commits.isEmpty(), "No starting commit provided");

        geogig = cli.getGeogig();

        LogOp op = geogig.command(LogOp.class).setTopoOrder(this.topo)
                .setFirstParentOnly(this.firstParent);

        for (String commit : this.commits) {
            if (commit.contains("..")) {
                checkParameter(this.commits.size() == 1,
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
        if (this.author != null && !this.author.isEmpty()) {
            op.setAuthor(this.author);
        }
        if (this.committer != null && !this.committer.isEmpty()) {
            op.setCommiter(this.committer);
        }
        if (this.skip != null) {
            op.setSkip(this.skip.intValue());
        }
        if (this.limit != null) {
            op.setLimit(this.limit.intValue());
        }
        if (this.since != null || this.until != null) {
            Date since = new Date(0);
            Date until = new Date();
            if (this.since != null) {
                since = new Date(geogig.command(ParseTimestamp.class).setString(this.since).call());
            }
            if (this.until != null) {
                until = new Date(geogig.command(ParseTimestamp.class).setString(this.until).call());
            }
            op.setTimeRange(Range.closed(since, until));
        }
        if (!this.pathNames.isEmpty()) {
            for (String s : this.pathNames) {
                op.addPath(s);
            }
        }
        Iterator<RevCommit> log = op.call();
        console = cli.getConsole();

        RawPrinter printer = new RawPrinter(this.changed);
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
                        .setOldVersion(commit.parentN(0).orElse(ObjectId.NULL))
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
            sb.append(p.getName().orElse("[unknown]")).append(' ');
            sb.append(p.getEmail().orElse("[unknown]")).append(' ');
            sb.append(p.getTimestamp()).append(' ').append(p.getTimeZoneOffset());
            return sb.toString();
        }

    }

}
