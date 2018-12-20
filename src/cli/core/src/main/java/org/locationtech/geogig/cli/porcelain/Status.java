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
import java.util.Iterator;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.StatusOp;
import org.locationtech.geogig.porcelain.StatusOp.StatusSummary;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.Iterators;

/**
 * Displays features that have differences between the index and the current HEAD commit and
 * features that have differences between the working tree and the index file. The first are what
 * you would commit by running geogig commit; the second are what you could commit by running geogig
 * add before running geogig commit.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig status [<options>]}
 * </ul>
 * 
 * @see Commit
 * @see Add
 */
@ReadOnly
@Parameters(commandNames = "status", commandDescription = "Show the working tree status")
public class Status extends AbstractCommand implements CLICommand {

    @Parameter(names = "--limit", description = "Limit number of displayed changes. Must be >= 0.")
    private Long limit = 50L;

    @Parameter(names = "--all", description = "Force listing all changes (overrides limit).")
    private boolean all = false;

    /**
     * Executes the status command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(limit >= 0, "Limit must be 0 or greater.");

        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        StatusOp op = geogig.command(StatusOp.class).setReportLimit(limit);
        StatusSummary summary = op.call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();
        checkParameter(currHead.isPresent(), "Repository has no HEAD.");

        if (currHead.get() instanceof SymRef) {
            final SymRef headRef = (SymRef) currHead.get();
            console.println("# On branch " + Ref.localName(headRef.getTarget()));
        } else {
            console.println("# Not currently on any branch.");
        }

        print(console, summary);

    }

    private void print(Console console, StatusSummary summary) throws IOException {
        long countStaged = summary.getCountStaged();
        long countUnstaged = summary.getCountUnstaged();
        long countConflicted = summary.getCountConflicts();

        if (countStaged + countUnstaged + countConflicted == 0) {
            console.println("nothing to commit (working directory clean)");
        }

        if (countStaged > 0) {
            console.println("# Changes to be committed:");
            console.println("#   (use \"geogig reset HEAD <path/to/fid>...\" to unstage)");
            console.println("#");
            try (AutoCloseableIterator<DiffEntry> iter = summary.getStaged().get()) {
                print(console, iter, Color.GREEN, countStaged);
            }
            console.println("#");
        }

        if (countConflicted > 0) {
            console.println("# Unmerged paths:");
            console.println(
                    "#   (use \"geogig add/rm <path/to/fid>...\" as appropriate to mark resolution");
            console.println("#");
            printUnmerged(console, summary.getConflicts().get(), Color.RED, countConflicted);
        }

        if (countUnstaged > 0) {
            console.println("# Changes not staged for commit:");
            console.println(
                    "#   (use \"geogig add <path/to/fid>...\" to update what will be committed");
            console.println(
                    "#   (use \"geogig checkout -- <path/to/fid>...\" to discard changes in working directory");
            console.println("#");
            try (AutoCloseableIterator<DiffEntry> iter = summary.getUnstaged().get()) {
                print(console, iter, Color.RED, countUnstaged);
            }
        }

    }

    /**
     * Prints the list of changes using the specified options
     * 
     * @param console the output console
     * @param changes an iterator of differences to print
     * @param color the color to use for the changes if color use is enabled
     * @param total the total number of changes
     * @throws IOException
     * @see DiffEntry
     */
    private void print(final Console console, final AutoCloseableIterator<DiffEntry> changes,
            final Color color,
            final long total) throws IOException {

        final int limit = all || this.limit == null ? Integer.MAX_VALUE : this.limit.intValue();

        StringBuilder sb = new StringBuilder();

        Ansi ansi = newAnsi(console, sb);

        DiffEntry entry;
        ChangeType type;
        String path;
        int cnt = 0;
        if (limit > 0) {
            AutoCloseableIterator<DiffEntry> changesIterator = changes;
            while (changesIterator.hasNext() && cnt < limit) {
                ++cnt;

                entry = changesIterator.next();
                type = entry.changeType();
                path = formatPath(entry);

                sb.setLength(0);
                ansi.a("#      ").fg(color).a(type.toString().toLowerCase()).a("  ").a(path)
                        .reset();
                console.println(ansi.toString());
            }
        }
        sb.setLength(0);
        ansi.a("# ").a(String.format("%,d", total)).a(" total.");
        console.println(ansi.toString());
    }

    private void printUnmerged(final Console console, Iterator<Conflict> iterator,
            final Color color, final long total) throws IOException {

        final int limit = all || this.limit == null ? Integer.MAX_VALUE : this.limit.intValue();

        StringBuilder sb = new StringBuilder();

        Ansi ansi = newAnsi(console, sb);

        String path;
        if (limit > 0) {
            iterator = Iterators.limit(iterator, limit);
            while (iterator.hasNext()) {
                Conflict c = iterator.next();
                path = c.getPath();
                sb.setLength(0);
                ansi.a("#      ").fg(color).a("unmerged").a("  ").a(path).reset();
                console.println(ansi.toString());
            }
        }

        sb.setLength(0);
        ansi.a("# ").a(String.format("%,d", total)).a(" total.");
        console.println(ansi.toString());
    }

    /**
     * Formats a DiffEntry for display
     * 
     * @param entry the DiffEntry to format
     * @return the formatted display string
     * @see DiffEntry
     */
    private String formatPath(DiffEntry entry) {
        String path;
        NodeRef oldObject = entry.getOldObject();
        NodeRef newObject = entry.getNewObject();
        if (oldObject == null) {
            path = newObject.path();
        } else if (newObject == null) {
            path = oldObject.path();
        } else {
            if (oldObject.path().equals(newObject.path())) {
                path = oldObject.path();
            } else {
                path = oldObject.path() + " -> " + newObject.path();
            }
        }
        return path;
    }
}
