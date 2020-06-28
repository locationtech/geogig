/* Copyright (c) 2012-2016 Boundless and others.
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.Iterators;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 */
@ReadOnly
@Command(name = "ls-tree", description = "Obtain information about features in the index and the working tree.")
public class LsTree extends AbstractCommand implements CLICommand {

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
            "--verbose" }, description = "Verbose output, include metadata, object id, and object type along with object path.")
    private boolean verbose;

    @Option(names = { "-s",
            "--size" }, description = "Print tree size (number of features). If verbose output was requested it takes precedence over size")
    private boolean printSize;

    public @Override void runInternal(final GeogigCLI cli) throws IOException {
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

        Function<NodeRef, CharSequence> printFunctor = new Function<NodeRef, CharSequence>() {

            private NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);

            public @Override CharSequence apply(NodeRef input) {
                numberFormat.setGroupingUsed(true);

                StringBuilder sb = new StringBuilder();
                Geogig geogig = cli.getGeogig();
                if (!verbose) {
                    sb.append(input.path());
                    if (printSize && input.getType().equals(TYPE.TREE)) {
                        RevTree tree = geogig.command(RevObjectParse.class)
                                .setObjectId(input.getObjectId()).call(RevTree.class).get();
                        sb.append(' ').append(numberFormat.format(tree.size()));
                    }
                } else {
                    Envelope env = new Envelope();
                    input.getNode().expand(env);
                    StringBuilder sbenv = new StringBuilder();
                    sbenv.append(Double.toString(env.getMinX())).append(";")
                            .append(Double.toString(env.getMaxX())).append(";")
                            .append(Double.toString(env.getMinY())).append(";")
                            .append(Double.toString(env.getMaxY()));
                    sb.append(input.getMetadataId().toString()).append(' ')
                            .append(input.getType().toString().toLowerCase()).append(' ')
                            .append(input.getObjectId().toString()).append(' ').append(input.path())
                            .append(' ').append(sbenv);
                    if (input.getType().equals(TYPE.TREE)) {
                        RevTree tree = geogig.command(RevObjectParse.class)
                                .setObjectId(input.getObjectId()).call(RevTree.class).get();
                        sb.append(' ').append(numberFormat.format(tree.size())).append(' ')
                                .append(tree.numTrees());
                    }
                }
                return sb;
            }
        };

        Iterator<CharSequence> lines = Iterators.transform(iter, printFunctor::apply);

        while (lines.hasNext()) {
            console.println(lines.next());
        }
        console.flush();
    }
}
