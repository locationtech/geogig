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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.geotools.util.Range;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.ParseTimestamp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Maps;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Shows the commit logs.
 * <p>
 * CLI proxy for {@link org.locationtech.geogig.porcelain.LogOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig log [<options>]}
 * </ul>
 * 
 * @see org.locationtech.geogig.porcelain.LogOp
 */
@ReadOnly
@Parameters(commandNames = "log", commandDescription = "Show commit logs")
public class Log extends AbstractCommand implements CLICommand {

    public enum LOG_DETAIL {
        SUMMARY, NAMES_ONLY, STATS, NOTHING
    };

    @ParametersDelegate
    public final LogArgs args = new LogArgs();

    private Map<ObjectId, String> refs;

    private GeoGIG geogig;

    private Console console;

    /**
     * Executes the log command using the provided options.
     * 
     * @param cli
     * @throws IOException
     * @see org.locationtech.geogig.cli.AbstractCommand#runInternal(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!(args.summary && args.oneline),
                "--summary and --oneline cannot be used together");
        checkParameter(!(args.stats && args.oneline),
                "--stats and --oneline cannot be used together");
        checkParameter(!(args.stats && args.oneline),
                "--name-only and --oneline cannot be used together");

        geogig = cli.getGeogig();

        LogOp op = geogig.command(LogOp.class).setTopoOrder(args.topo)
                .setFirstParentOnly(args.firstParent);

        refs = Maps.newHashMap();
        if (args.decoration) {
            Optional<Ref> head = geogig.command(RefParse.class).setName(Ref.HEAD).call();
            refs.put(head.get().getObjectId(), Ref.HEAD);
            ImmutableSet<Ref> set = geogig.command(ForEachRef.class)
                    .setPrefixFilter(Ref.REFS_PREFIX).call();
            for (Ref ref : set) {
                ObjectId id = ref.getObjectId();
                if (refs.containsKey(id)) {
                    refs.put(id, refs.get(id) + ", " + ref.getName());
                } else {
                    refs.put(id, ref.getName());
                }
            }
        }
        if (args.all) {
            ImmutableSet<Ref> refs = geogig.command(ForEachRef.class)
                    .setPrefixFilter(Ref.REFS_PREFIX).call();
            List<ObjectId> list = Lists.newArrayList();
            for (Ref ref : refs) {
                list.add(ref.getObjectId());
            }
            Optional<Ref> head = geogig.command(RefParse.class).setName(Ref.HEAD).call();
            if (head.isPresent()) {
                Ref ref = head.get();
                if (ref instanceof SymRef) {
                    ObjectId id = ref.getObjectId();
                    list.remove(id);
                    list.add(id);// put the HEAD ref in the last position, to give it preference
                }
            }
            for (ObjectId id : list) {
                op.addCommit(id);
            }
        } else if (args.branch != null) {
            Optional<Ref> obj = geogig.command(RefParse.class).setName(args.branch).call();
            checkParameter(obj.isPresent(), "Wrong branch name: " + args.branch);
            op.addCommit(obj.get().getObjectId());
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
                if (args.all) {
                    throw new InvalidParameterException(
                            "Cannot specify 'until' commit when listing all branches");
                }
            }
            op.setTimeRange(new Range<Date>(Date.class, since, until));
        }
        if (!args.sinceUntilPaths.isEmpty()) {
            List<String> sinceUntil = ImmutableList
                    .copyOf((Splitter.on("..").split(args.sinceUntilPaths.get(0))));
            checkParameter(sinceUntil.size() == 1 || sinceUntil.size() == 2,
                    "Invalid refSpec format, expected [<until>]|[<since>..<until>]: %s",
                    args.sinceUntilPaths.get(0));

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
                if (args.all) {
                    throw new InvalidParameterException(
                            "Cannot specify 'until' commit when listing all branches");
                }
                Optional<ObjectId> until;
                until = geogig.command(RevParse.class).setRefSpec(untilRefSpec).call();
                checkParameter(until.isPresent(), "Object not found '%s'", sinceRefSpec);
                op.setUntil(until.get());
            }
        }
        if (!args.pathNames.isEmpty()) {
            for (String s : args.pathNames) {
                op.addPath(s);
            }
        }
        Iterator<RevCommit> log = op.call();
        this.console = cli.getConsole();
        if (!log.hasNext()) {
            console.println("No commits to show");
            console.flush();
            return;
        }

        LogEntryPrinter printer;
        if (args.oneline) {
            printer = new OneLineConverter();
        } else {
            LOG_DETAIL detail;
            if (args.summary) {
                detail = LOG_DETAIL.SUMMARY;
            } else if (args.names) {
                detail = LOG_DETAIL.NAMES_ONLY;
            } else if (args.stats) {
                detail = LOG_DETAIL.STATS;
            } else {
                detail = LOG_DETAIL.NOTHING;
            }

            printer = new StandardConverter(detail, geogig.getPlatform());
        }

        while (log.hasNext()) {
            printer.print(log.next());
            console.flush();
        }
    }

    interface LogEntryPrinter {

        /**
         * @param geogig
         * @param console
         * @param entry
         * @throws IOException
         */
        void print(RevCommit commit) throws IOException;

    }

    private class OneLineConverter implements LogEntryPrinter {

        @Override
        public void print(RevCommit commit) throws IOException {
            Ansi ansi = newAnsi(console);
            ansi.fg(Color.YELLOW).a(getIdAsString(commit.getId())).reset();
            String message = Strings.nullToEmpty(commit.getMessage());
            String title = Splitter.on('\n').split(message).iterator().next();
            ansi.a(" ").a(title);
            console.println(ansi.toString());
        }

    }

    private class StandardConverter implements LogEntryPrinter {

        private SimpleDateFormat DATE_FORMAT;

        private long now;

        private LOG_DETAIL detail;

        public StandardConverter(final LOG_DETAIL detail, final Platform platform) {
            now = platform.currentTimeMillis();
            DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            this.detail = detail;
        }

        @Override
        public void print(RevCommit commit) throws IOException {
            Ansi ansi = newAnsi(console);

            ansi.a("Commit:  ").fg(Color.YELLOW).a(getIdAsString(commit.getId())).reset().newline();
            if (commit.getParentIds().size() > 1) {
                ansi.a("Merge: ");
                for (ObjectId parent : commit.getParentIds()) {
                    ansi.a(getIdAsString(parent)).a(" ");
                }
                ansi.newline();
            }
            ansi.a("Author:  ").fg(Color.GREEN).a(formatPerson(commit.getAuthor())).reset()
                    .newline();

            final long timestamp = commit.getAuthor().getTimestamp();

            int timeZoneOffset = commit.getAuthor().getTimeZoneOffset();
            if (args.utcDateFormat) {
                timeZoneOffset = 0;
            }
            String friendlyString = estimateSince(now, timestamp);
            DATE_FORMAT.getCalendar().getTimeZone().setRawOffset(timeZoneOffset);
            String formattedDate = DATE_FORMAT.format(timestamp);

            ansi.a("Date:    (").fg(Color.RED).a(friendlyString).reset().a(") ").a(formattedDate)
                    .newline();
            ansi.a("Subject: ").a(commit.getMessage()).newline();
            if ((detail.equals(LOG_DETAIL.NAMES_ONLY)) && commit.getParentIds().size() == 1) {
                ansi.a("Affected paths:").newline();
                try (AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                        .setOldVersion(commit.parentN(0).get()).setNewVersion(commit.getId())
                        .call()) {
                    DiffEntry diffEntry;
                    while (diff.hasNext()) {
                        diffEntry = diff.next();
                        String path = diffEntry.isDelete() ? diffEntry.oldPath()
                                : diffEntry.newPath();
                        ansi.a("\t" + path).newline();
                    }
                }
            }
            if (detail.equals(LOG_DETAIL.STATS)) {

                String oldSpec = commit.parentN(0).isPresent() ? commit.parentN(0).get().toString()
                        : ObjectId.NULL.toString();
                String newSpec = commit.getId().toString();
                DiffObjectCount diffCount = geogig.command(DiffCount.class).setOldVersion(oldSpec)
                        .setNewVersion(newSpec).call();

                long featuresAdded = diffCount.getFeaturesAdded();
                long featuresChanged = diffCount.getFeaturesChanged();
                long featuresRemoved = diffCount.getFeaturesRemoved();

                ansi.a("Changes:");
                ansi.fg(Color.GREEN).a(featuresAdded).reset().a(" features added, ")
                        .fg(Color.YELLOW).a(featuresChanged).reset().a(" changed, ").fg(Color.RED)
                        .a(featuresRemoved).reset().a(" deleted.").reset().newline();
            }

            console.println(ansi.toString());
            if (detail.equals(LOG_DETAIL.SUMMARY) && commit.getParentIds().size() == 1) {
                ansi.a("Changes:").newline();
                try (AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                        .setOldVersion(commit.parentN(0).get()).setNewVersion(commit.getId())
                        .call()) {
                    DiffEntry diffEntry;
                    while (diff.hasNext()) {
                        diffEntry = diff.next();
                        if (detail.equals(LOG_DETAIL.SUMMARY)) {
                            new FullDiffPrinter(true, false).print(geogig, console, diffEntry);
                        }

                    }
                }
            }
        }
    }

    /**
     * Converts a RevPerson for into a readable string.
     * 
     * @param person the person to format.
     * @return the formatted string
     * @see RevPerson
     */
    private String formatPerson(RevPerson person) {
        StringBuilder sb = new StringBuilder();
        sb.append(person.getName().or("<name not set>"));

        if (person.getEmail().isPresent()) {
            sb.append(" <").append(person.getEmail().get()).append('>');
        }
        return sb.toString();
    }

    /**
     * Converts a timestamp into a readable string that represents the rough time since that
     * timestamp.
     * 
     * @param now
     * @param timestamp
     * @return
     */
    private String estimateSince(final long now, long timestamp) {
        long diff = now - timestamp;
        final long seconds = 1000;
        final long minutes = seconds * 60;
        final long hours = minutes * 60;
        final long days = hours * 24;
        final long weeks = days * 7;
        final long months = days * 30;
        final long years = days * 365;

        if (diff > years) {
            return diff / years + " years ago";
        }
        if (diff > months) {
            return diff / months + " months ago";
        }
        if (diff > weeks) {
            return diff / weeks + " weeks ago";
        }
        if (diff > days) {
            return diff / days + " days ago";
        }
        if (diff > hours) {
            return diff / hours + " hours ago";
        }
        if (diff > minutes) {
            return diff / minutes + " minutes ago";
        }
        if (diff > seconds) {
            return diff / seconds + " seconds ago";
        }
        return "just now";
    }

    /**
     * Returns an Id as a string, decorating or abbreviating it if needed
     * 
     * @param id
     * @return
     */
    private String getIdAsString(ObjectId id) {
        StringBuilder sb = new StringBuilder();
        if (args.abbrev) {
            sb.append(id.toString().substring(0, 8));
        } else {
            sb.append(id.toString());
        }
        if (refs.containsKey(id)) {
            sb.append(" (");
            sb.append(refs.get(id));
            sb.append(')');
        }

        return sb.toString();
    }
}
