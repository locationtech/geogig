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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.jts.geom.Envelope;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 *
 */
@ReadOnly
@Parameters(commandNames = "ls-tree", commandDescription = "Obtain information about features in the index and the working tree.")
public class LsTree extends AbstractCommand implements CLICommand {

    @Parameter(description = "<[refspec]:[path]>", arity = 1)
    private List<String> refList = Lists.newArrayList();

    @Parameter(names = { "-t" }, description = "Show tree entries even when going to recurse them. Has no effect if -r was not passed. -d implies -t.")
    private boolean includeTrees;

    @Parameter(names = { "-d" }, description = "Show only the named tree entry itself, not its children.")
    private boolean onlyTrees;

    @Parameter(names = { "-r" }, description = "Recurse into sub-trees.")
    private boolean recursive;

    @Parameter(names = { "-v", "--verbose" }, description = "Verbose output, include metadata, object id, and object type along with object path.")
    private boolean verbose;

    @Parameter(names = { "-s", "--size" }, description = "Print tree size (number of features). If verbose output was requested it takes precedence over size")
    private boolean printSize;

    @Override
    public void runInternal(final GeogigCLI cli) throws IOException {
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

            @Override
            public CharSequence apply(NodeRef input) {
                numberFormat.setGroupingUsed(true);

                StringBuilder sb = new StringBuilder();
                GeoGIG geogig = cli.getGeogig();
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
                            .append(input.getObjectId().toString()).append(' ')
                            .append(input.path()).append(' ').append(sbenv);
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

        Iterator<CharSequence> lines = Iterators.transform(iter, printFunctor);

        while (lines.hasNext()) {
            console.println(lines.next());
        }
        console.flush();
    }
}
