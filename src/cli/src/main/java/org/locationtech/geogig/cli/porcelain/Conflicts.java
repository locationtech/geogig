/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.CatObject;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsReadOp;
import org.locationtech.geogig.api.porcelain.FeatureNodeRefFromRefspec;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Show existing conflicts
 * 
 * @see MergeOp
 */
// Currently it just print conflict descriptions, so they can be used by another tool instead.
@ObjectDatabaseReadOnly
@Parameters(commandNames = "conflicts", commandDescription = "Shows existing conflicts")
public class Conflicts extends AbstractCommand implements CLICommand {

    @Parameter(description = "<path> [<path>...]")
    private List<String> paths = Lists.newArrayList();

    @Parameter(names = { "--diff" }, description = "Show diffs instead of full element descriptions")
    private boolean previewDiff;

    @Parameter(names = { "--ids-only" }, description = "Just show ids of elements")
    private boolean idsOnly;

    @Parameter(names = { "--refspecs-only" }, description = "Just show refspecs of elements")
    private boolean refspecsOnly;

    private GeoGIG geogig;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!(idsOnly && previewDiff),
                "Cannot use --diff and --ids-only at the same time");
        checkParameter(!(refspecsOnly && previewDiff),
                "Cannot use --diff and --refspecs-only at the same time");
        checkParameter(!(refspecsOnly && idsOnly),
                "Cannot use --ids-only and --refspecs-only at the same time");

        geogig = cli.getGeogig();
        List<Conflict> conflicts = geogig.command(ConflictsReadOp.class).call();

        if (conflicts.isEmpty()) {
            cli.getConsole().println("No elements need merging.");
            return;
        }
        for (Conflict conflict : conflicts) {
            if (paths.isEmpty() || paths.contains(conflict.getPath())) {
                if (previewDiff) {
                    printConflictDiff(conflict, cli.getConsole(), geogig);
                } else if (idsOnly) {
                    cli.getConsole().println(conflict.toString());
                } else if (refspecsOnly) {
                    printRefspecs(conflict, cli.getConsole(), geogig);
                } else {
                    printConflict(conflict, cli.getConsole(), geogig);
                }
            }
        }
    }

    private File getRebaseFolder() {
        URL dir = geogig.command(ResolveGeogigDir.class).call().get();
        File rebaseFolder = new File(dir.getFile(), "rebase-apply");
        return rebaseFolder;
    }

    private void printRefspecs(Conflict conflict, ConsoleReader console, GeoGIG geogig)
            throws IOException {
        ObjectId theirsHeadId;
        Optional<Ref> mergeHead = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        if (mergeHead.isPresent()) {
            theirsHeadId = mergeHead.get().getObjectId();
        } else {
            File branchFile = new File(getRebaseFolder(), "branch");
            Preconditions
                    .checkState(branchFile.exists(), "Cannot find merge/rebase head reference");
            try {
                String currentBranch = Files.readFirstLine(branchFile, Charsets.UTF_8);
                Optional<Ref> rebaseHead = geogig.command(RefParse.class).setName(currentBranch)
                        .call();
                theirsHeadId = rebaseHead.get().getObjectId();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read current branch info file");
            }

        }
        Optional<RevCommit> theirsHead = geogig.command(RevObjectParse.class)
                .setObjectId(theirsHeadId).call(RevCommit.class);
        ObjectId oursHeadId = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call().get()
                .getObjectId();
        Optional<RevCommit> oursHead = geogig.command(RevObjectParse.class).setObjectId(oursHeadId)
                .call(RevCommit.class);
        Optional<ObjectId> commonAncestor = geogig.command(FindCommonAncestor.class)
                .setLeft(theirsHead.get()).setRight(oursHead.get()).call();
        String ancestorPath = commonAncestor.get().toString() + ":" + conflict.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(conflict.getPath());
        sb.append(" ");
        sb.append(ancestorPath);
        sb.append(" ");
        sb.append(oursHeadId.toString() + ":" + conflict.getPath());
        sb.append(" ");
        sb.append(theirsHeadId.toString() + ":" + conflict.getPath());
        console.println(sb.toString());
    }

    private void printConflictDiff(Conflict conflict, ConsoleReader console, GeoGIG geogig)
            throws IOException {
        FullDiffPrinter diffPrinter = new FullDiffPrinter(false, true);
        console.println("---" + conflict.getPath() + "---");

        ObjectId theirsHeadId;
        Optional<Ref> mergeHead = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        if (mergeHead.isPresent()) {
            theirsHeadId = mergeHead.get().getObjectId();
        } else {
            File branchFile = new File(getRebaseFolder(), "branch");
            Preconditions
                    .checkState(branchFile.exists(), "Cannot find merge/rebase head reference");
            try {
                String currentBranch = Files.readFirstLine(branchFile, Charsets.UTF_8);
                Optional<Ref> rebaseHead = geogig.command(RefParse.class).setName(currentBranch)
                        .call();
                theirsHeadId = rebaseHead.get().getObjectId();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read current branch info file");
            }

        }
        Optional<RevCommit> theirsHead = geogig.command(RevObjectParse.class)
                .setObjectId(theirsHeadId).call(RevCommit.class);
        ObjectId oursHeadId = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call().get()
                .getObjectId();
        Optional<RevCommit> oursHead = geogig.command(RevObjectParse.class).setObjectId(oursHeadId)
                .call(RevCommit.class);
        Optional<ObjectId> commonAncestor = geogig.command(FindCommonAncestor.class)
                .setLeft(theirsHead.get()).setRight(oursHead.get()).call();

        String ancestorPath = commonAncestor.get().toString() + ":" + conflict.getPath();
        Optional<NodeRef> ancestorNodeRef = geogig.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(ancestorPath).call();
        String path = Ref.ORIG_HEAD + ":" + conflict.getPath();
        Optional<NodeRef> oursNodeRef = geogig.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(path).call();
        DiffEntry diffEntry = new DiffEntry(ancestorNodeRef.orNull(), oursNodeRef.orNull());
        console.println("Ours");
        diffPrinter.print(geogig, console, diffEntry);
        path = theirsHeadId + ":" + conflict.getPath();
        Optional<NodeRef> theirsNodeRef = geogig.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(path).call();
        diffEntry = new DiffEntry(ancestorNodeRef.orNull(), theirsNodeRef.orNull());
        console.println("Theirs");
        diffPrinter.print(geogig, console, diffEntry);

    }

    private void printConflict(Conflict conflict, ConsoleReader console, GeoGIG geogig)
            throws IOException {

        console.println(conflict.getPath());
        console.println();
        printObject("Ancestor", conflict.getAncestor(), console, geogig);
        console.println();
        printObject("Ours", conflict.getOurs(), console, geogig);
        console.println();
        printObject("Theirs", conflict.getTheirs(), console, geogig);
        console.println();

    }

    private void printObject(String name, ObjectId id, ConsoleReader console, GeoGIG geogig)
            throws IOException {

        console.println(name + "\t" + id.toString());
        if (!id.isNull()) {
            Optional<RevObject> obj = geogig.command(RevObjectParse.class).setObjectId(id).call();
            CharSequence s = geogig.command(CatObject.class)
                    .setObject(Suppliers.ofInstance(obj.get())).call();
            console.println(s);
        }

    }

}