/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.porcelain;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.Patch;
import org.locationtech.geogig.api.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.api.porcelain.CreatePatchOp;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * Store changes between two version of the repository in a patch file
 * 
 * Usage is identical to that of the diff command, but adding a destination file where the patch is
 * to be saved
 * 
 * @see Diff
 */
@ReadOnly
@Parameters(commandNames = "format-patch", commandDescription = "Creates a patch with a set of changes")
public class FormatPatch extends AbstractCommand implements CLICommand {

    @Parameter(description = "[<commit> [<commit>]] [-- <path>...]", arity = 2)
    private List<String> refSpec = Lists.newArrayList();

    @Parameter(names = "--", hidden = true, variableArity = true)
    private List<String> paths = Lists.newArrayList();

    @Parameter(names = { "-f", "--file" }, description = "The patch file")
    private String file;

    @Parameter(names = "--cached", description = "compares the specified tree (commit, branch, etc) and the staging area")
    private boolean cached;

    /**
     * Executes the format-patch command with the specified options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(refSpec.size() < 3, "Commit list is too long :%s", refSpec);

        GeoGIG geogig = cli.getGeogig();
        checkParameter(file != null, "Patch file not specified");

        DiffOp diff = geogig.command(DiffOp.class).setReportTrees(true);

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

        diff.setOldVersion(oldVersion).setNewVersion(newVersion).setCompareIndex(cached);

        Iterator<DiffEntry> entries;
        if (paths.isEmpty()) {
            entries = diff.setProgressListener(cli.getProgressListener()).call();
        } else {
            entries = Iterators.emptyIterator();
            for (String path : paths) {
                Iterator<DiffEntry> moreEntries = diff.setFilter(path)
                        .setProgressListener(cli.getProgressListener()).call();
                entries = Iterators.concat(entries, moreEntries);
            }
        }

        if (!entries.hasNext()) {
            cli.getConsole().println("No differences found");
            return;
        }

        Patch patch = geogig.command(CreatePatchOp.class).setDiffs(entries).call();
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
        PatchSerializer.write(out, patch);

    }

    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : null;
    }

    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : null;
    }

}
