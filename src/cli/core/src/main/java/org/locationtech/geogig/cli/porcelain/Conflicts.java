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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.CatObject;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.porcelain.FeatureNodeRefFromRefspec;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.impl.Blobs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

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

    @Parameter(names = {
            "--diff" }, description = "Show diffs instead of full element descriptions")
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
        Iterator<Conflict> conflicts = geogig.command(ConflictsQueryOp.class).call();

        if (!conflicts.hasNext()) {
            cli.getConsole().println("No elements need merging.");
            return;
        }
        while (conflicts.hasNext()) {
            Conflict conflict = conflicts.next();
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

    private void printRefspecs(Conflict conflict, Console console, GeoGIG geogig)
            throws IOException {
        ObjectId theirsHeadId = getTheirsHeadId();
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

    private void printConflictDiff(Conflict conflict, Console console, GeoGIG geogig)
            throws IOException {
        FullDiffPrinter diffPrinter = new FullDiffPrinter(false, true);
        console.println("---" + conflict.getPath() + "---");
        ObjectId theirsHeadId = getTheirsHeadId();
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

    private void printConflict(Conflict conflict, Console console, GeoGIG geogig)
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

    private ObjectId getTheirsHeadId() {
        ObjectId theirsHeadId;
        Optional<Ref> mergeHead = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        if (mergeHead.isPresent()) {
            // It was a merge
            theirsHeadId = mergeHead.get().getObjectId();
        } else {
            // It was a rebase
            final Optional<byte[]> branchBlob = geogig.getRepository().blobStore()
                    .getBlob(RebaseOp.REBASE_BRANCH_BLOB);
            Preconditions.checkState(branchBlob.isPresent(),
                    "Cannot find merge/rebase head reference");
            List<String> branchLines = Blobs.readLines(branchBlob);
            String currentBranch = branchLines.get(0);
            Optional<Ref> rebaseHead = geogig.command(RefParse.class).setName(currentBranch).call();
            Preconditions.checkState(rebaseHead.isPresent(), "Rebase head could not be resolved.");
            theirsHeadId = rebaseHead.get().getObjectId();
        }
        return theirsHeadId;
    }

    private void printObject(String name, ObjectId id, Console console, GeoGIG geogig)
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