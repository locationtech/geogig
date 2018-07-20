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
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.fusesource.jansi.Ansi;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.AnsiDecorator;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.DiffBounds;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.diff.DiffSummary;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 * Shows changes between commits, commits and working tree, etc.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig diff [-- <path>...]}: compare working tree and index
 * <li> {@code geogig diff <commit> [-- <path>...]}: compare the working tree with the given commit
 * <li> {@code geogig diff --cached [-- <path>...]}: compare the index with the HEAD commit
 * <li> {@code geogig diff --cached <commit> [-- <path>...]}: compare the index with the given commit
 * <li> {@code geogig diff <commit1> <commit2> [-- <path>...]}: compare {@code commit1} with
 * {@code commit2}, where {@code commit1} is the eldest or left side of the diff.
 * </ul>
 * 
 * @see DiffOp
 */
@ReadOnly
@Parameters(commandNames = "diff", commandDescription = "Show changes between commits, commit and working tree, etc")
public class Diff extends AbstractCommand implements CLICommand {

    @Parameter(description = "[<commit> [<commit>]] [-- <path>...]", arity = 2)
    private List<String> refSpec = Lists.newArrayList();

    @Parameter(names = { "--path", "-p" }, hidden = true, variableArity = true)
    private List<String> paths = Lists.newArrayList();

    @Parameter(names = "--cached", description = "compares the specified tree (commit, branch, etc) and the staging area")
    private boolean cached;

    @Parameter(names = "--summary", description = "List only summary of changes")
    private boolean summary;

    @Parameter(names = "--nogeom", description = "Do not show detailed coordinate changes in geometries")
    private boolean nogeom;

    @Parameter(names = "--bounds", description = "Show only the bounds of the difference between the two trees")
    private boolean bounds;

    @Parameter(names = "--crs", description = "Coordinate reference system for --bounds (defaults to EPSG:4326 with lon/lat axis order)")
    private String boundsCrs;

    @Parameter(names = "--count", description = "Only count the number of changes between the two trees")
    private boolean count;

    /**
     * Executes the diff command with the specified options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(refSpec.size() <= 2, "Commit list is too long :%s", refSpec);
        checkParameter(!(nogeom && summary), "Only one printing mode allowed");
        checkParameter(!(bounds && count), "Only one of --bounds or --count is allowed");
        checkParameter(!(cached && refSpec.size() > 1),
                "--cached allows zero or one ref specs to compare the index with.");

        GeoGIG geogig = cli.getGeogig();

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

        List<String> paths = removeEmptyPaths();
        if (bounds) {
            DiffBounds diff = geogig.command(DiffBounds.class).setOldVersion(oldVersion)
                    .setNewVersion(newVersion).setCompareIndex(cached);
            diff.setPathFilters(paths);
            CoordinateReferenceSystem crs = parseCrs();
            if (crs != null) {
                diff.setCRS(crs);
            }
            DiffSummary<BoundingBox, BoundingBox> diffBounds = diff.call();
            BoundsDiffPrinter.print(geogig, cli.getConsole(), diffBounds);
            return;
        }
        if (count) {
            if (oldVersion == null) {
                oldVersion = Ref.HEAD;
            }
            if (newVersion == null) {
                newVersion = cached ? Ref.STAGE_HEAD : Ref.WORK_HEAD;
            }
            DiffCount cdiff = geogig.command(DiffCount.class).setOldVersion(oldVersion)
                    .setNewVersion(newVersion);
            cdiff.setFilter(paths);
            DiffObjectCount count = cdiff.call();
            Console console = cli.getConsole();
            console.println(String.format("Trees: added %,d, changed %,d, removed %,d",
                    count.getTreesAdded(), count.getTreesChanged(), count.getTreesRemoved()));
            console.println(String.format(
                    "Features: added %,d, changed %,d, removed %,d, total: %,d",
                    count.getFeaturesAdded(), count.getFeaturesChanged(),
                    count.getFeaturesRemoved(), count.featureCount()));
            console.flush();
            return;
        }

        try (AutoCloseableIterator<DiffEntry> entries = buildEntries(cli, oldVersion, newVersion)) {
            if (!entries.hasNext()) {
                cli.getConsole().println("No differences found");
                return;
            }

            DiffPrinter printer;
            if (summary) {
                printer = new SummaryDiffPrinter();
            } else {
                printer = new FullDiffPrinter(nogeom, false);
            }

            DiffEntry entry;
            while (entries.hasNext()) {
                entry = entries.next();
                printer.print(geogig, cli.getConsole(), entry);
            }
        }
    }

    private AutoCloseableIterator<DiffEntry> buildEntries(GeogigCLI cli, String oldVersion,
            String newVersion) {
        DiffOp diff = cli.getGeogig().command(DiffOp.class);
        diff.setOldVersion(oldVersion).setNewVersion(newVersion).setCompareIndex(cached);

        AutoCloseableIterator<DiffEntry> entries;
        if (paths.isEmpty()) {
            entries = diff.setProgressListener(cli.getProgressListener()).call();
        } else {
            entries = AutoCloseableIterator.emptyIterator();
            for (String path : paths) {
                AutoCloseableIterator<DiffEntry> moreEntries = diff.setFilter(path)
                        .setProgressListener(cli.getProgressListener()).call();
                entries = AutoCloseableIterator.concat(entries, moreEntries);
            }
        }
        return entries;
    }

    private List<String> removeEmptyPaths() {
        List<String> paths = Lists.newLinkedList(this.paths);
        for (Iterator<String> it = paths.iterator(); it.hasNext();) {
            if (Strings.isNullOrEmpty(it.next())) {
                it.remove();
            }
        }
        return paths;
    }

    private CoordinateReferenceSystem parseCrs() {
        if (boundsCrs == null) {
            return null;
        }
        try {
            return CRS.decode(boundsCrs, true);
        } catch (Exception e) {
            throw new InvalidParameterException(String.format("Unrecognized CRS: '%s'", boundsCrs));
        }
    }

    @Nullable
    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : null;
    }

    @Nullable
    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : null;
    }

    private static final class BoundsDiffPrinter {

        public static void print(GeoGIG geogig, Console console,
                DiffSummary<BoundingBox, BoundingBox> diffBounds) throws IOException {

            BoundingBox left = diffBounds.getLeft();
            BoundingBox right = diffBounds.getRight();
            Optional<BoundingBox> mergedResult = diffBounds.getMergedResult();
            BoundingBox both = new ReferencedEnvelope();
            if (mergedResult.isPresent()) {
                both = mergedResult.get();
            }

            Ansi ansi = AnsiDecorator.newAnsi(console.isAnsiSupported());

            ansi.a("left:  ").a(bounds(left)).newline();
            ansi.a("right: ").a(bounds(right)).newline();
            ansi.a("both:  ").a(bounds(both)).newline();
            ansi.a("CRS:   ").a(CRS.toSRS(left.getCoordinateReferenceSystem())).newline();

            console.print(ansi.toString());
        }

        private static CharSequence bounds(BoundingBox b) {
            if (b.isEmpty()) {
                return "<empty>";
            }
            return String.format("%f,%f,%f,%f", b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY());
        }

    }
}
