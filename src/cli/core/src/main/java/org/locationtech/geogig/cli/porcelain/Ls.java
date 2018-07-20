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
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 *
 */
@ReadOnly
@Parameters(commandNames = "ls", commandDescription = "Obtain information about features in the index and the working tree.")
public class Ls extends AbstractCommand implements CLICommand {

    @Parameter(description = "<[refspec]:[path]>", arity = 1)
    private List<String> refList = Lists.newArrayList();

    @Parameter(names = { "-t" }, description = "Show tree entries even when going to recurse them. Has no effect if -r was not passed. -d implies -t.")
    private boolean includeTrees;

    @Parameter(names = { "-d" }, description = "Show only the named tree entry itself, not its children.")
    private boolean onlyTrees;

    @Parameter(names = { "-r" }, description = "Recurse into sub-trees.")
    private boolean recursive;

    @Parameter(names = { "-v", "--verbose" }, description = "Verbose output, include metadata and object id")
    private boolean verbose;

    @Parameter(names = { "-a", "--abbrev" }, description = "Instead of showing the full 40-byte hexadecimal object lines, show only a partial prefix. "
            + "Non default number of digits can be specified with --abbrev <n>.")
    private Integer abbrev;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
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

            @Override
            public CharSequence apply(NodeRef input) {
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
                    sb.append(' ').append(abbrev(input.getMetadataId())).append(' ')
                            .append(abbrev(input.getObjectId()));
                }
                return sb.toString();
            }

            private String abbrev(ObjectId oid) {
                return abbrev == null ? oid.toString() : oid.toString().substring(0,
                        abbrev.intValue());
            }
        };

        Iterator<CharSequence> lines = Iterators.transform(iter, printFunctor);

        while (lines.hasNext()) {
            console.println(lines.next());
        }
        console.flush();
    }
}
