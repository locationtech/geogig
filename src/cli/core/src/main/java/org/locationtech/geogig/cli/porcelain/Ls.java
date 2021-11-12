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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;

import com.google.common.collect.Iterators;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 */
@ReadOnly
@Command(name = "ls", description = "Obtain information about features in the index and the working tree.")
public class Ls extends AbstractCommand implements CLICommand {

    @Parameters(description = "<[refspec]:[path]>", arity = "0..1")
    private List<String> refList = new ArrayList<>();

    @Option(names = {
            "-t" }, description = "Show tree entries even when going to recurse them. Has no effect if -r was not passed. -d implies -t.")
    private boolean includeTrees;

    @Option(names = {
            "-d" }, description = "Show only the named tree entry itself, not its children.")
    private boolean onlyTrees;

    @Option(names = { "-r" }, description = "Recurse into sub-trees.")
    private boolean recursive;

    @Option(names = { "-v",
            "--verbose" }, description = "Verbose output, include metadata and object id")
    private boolean verbose;

    @Option(names = { "-a",
            "--abbrev" }, description = "Instead of showing the full 40-byte hexadecimal object lines, show only a partial prefix. "
                    + "Non default number of digits can be specified with --abbrev <n>.")
    private Integer abbrev;

    public @Override void runInternal(GeogigCLI cli) throws IOException {
        String ref;
        if (refList.isEmpty()) {
            ref = null;
        } else {
            ref = refList.get(0);
        }
        Strategy lsStrategy = Strategy.CHILDREN;
        if (recursive) {
            if (includeTrees) {
                lsStrategy = Strategy.DEPTHFIRST;
            } else if (onlyTrees) {
                lsStrategy = Strategy.DEPTHFIRST_ONLY_TREES;
            } else {
                lsStrategy = Strategy.DEPTHFIRST_ONLY_FEATURES;
            }
        } else {
            if (onlyTrees) {
                lsStrategy = Strategy.TREES_ONLY;
            }
        }
        Iterator<NodeRef> iter = cli.getGeogig().command(LsTreeOp.class).setReference(ref)
                .setStrategy(lsStrategy).call();

        final Console console = cli.getConsole();
        if (!iter.hasNext()) {
            if (ref == null) {
                console.println("The working tree is empty");
            } else {
                console.println("The specified path is empty");
            }
            return;
        }

        int depth = 0;
        if (ref == null) {
            console.println("Root tree/");
        } else {
            console.println(ref + "/");
            depth = ref.split("/").length - 1;
        }

        final int rootDepth = depth;

        Function<NodeRef, CharSequence> printFunctor = new Function<NodeRef, CharSequence>() {

            public @Override CharSequence apply(NodeRef input) {
                String path = input.path();
                int depth = path.split("/").length - rootDepth;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    sb.append('\t');
                }
                sb.append(input.getNode().getName());
                if (input.getType().equals(TYPE.TREE)) {
                    sb.append('/');
                }
                if (verbose) {
                    sb.append(' ').append(abbrev(input.metadataId())).append(' ')
                            .append(abbrev(input.getObjectId()));
                }
                return sb.toString();
            }

            private String abbrev(ObjectId oid) {
                return abbrev == null ? oid.toString()
                        : oid.toString().substring(0, abbrev.intValue());
            }
        };

        Iterator<CharSequence> lines = Iterators.transform(iter, printFunctor::apply);

        while (lines.hasNext()) {
            console.println(lines.next());
        }
        console.flush();
    }
}
