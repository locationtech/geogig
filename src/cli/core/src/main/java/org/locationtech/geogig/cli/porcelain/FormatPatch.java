/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.porcelain.CreatePatchOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Store changes between two version of the repository in a patch file
 * 
 * Usage is identical to that of the diff command, but adding a destination file where the patch is
 * to be saved
 * 
 * @see Diff
 */
@ReadOnly
@Command(name = "format-patch", description = "Creates a patch with a set of changes")
public class FormatPatch extends AbstractCommand implements CLICommand {

    @Parameters(description = "[<commit> [<commit>]]")
    private List<String> refSpec = new ArrayList<>();

    @Option(names = { "--path", "-p" }, description = "List of tree/feature paths to filter by")
    private List<String> paths = new ArrayList<>();

    @Option(names = { "-f", "--file" }, description = "The patch file")
    private String file;

    @Option(names = "--cached", description = "compares the specified tree (commit, branch, etc) and the staging area")
    private boolean cached;

    /**
     * Executes the format-patch command with the specified options.
     */
    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(refSpec.size() < 3, "Commit list is too long :%s", refSpec);

        Geogig geogig = cli.getGeogig();
        checkParameter(file != null, "Patch file not specified");

        try (AutoCloseableIterator<DiffEntry> entries = buildEntries(cli)) {
            if (!entries.hasNext()) {
                cli.getConsole().println("No differences found");
                return;
            }

            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(entries).call();
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
            PatchSerializer.write(out, patch);
        }

    }

    private AutoCloseableIterator<DiffEntry> buildEntries(GeogigCLI cli) {
        DiffOp diff = cli.getGeogig().command(DiffOp.class).setReportTrees(true);

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

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

    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : null;
    }

    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : null;
    }

}
