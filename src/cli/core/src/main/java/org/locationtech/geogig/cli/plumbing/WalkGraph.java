/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.WalkGraphOp;
import org.locationtech.geogig.plumbing.WalkGraphOp.Listener;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 */
@ReadOnly
@Command(name = "walk-graph", description = "Visit objects in history graph in post order (referenced objects before referring objects)")
public class WalkGraph extends AbstractCommand implements CLICommand {

    @Parameters(description = "<[refspec]:[path]>", arity = "0..1")
    private List<String> refList = new ArrayList<>();

    @Option(names = { "-v",
            "--verbose" }, description = "Verbose output, include metadata, object id, and object type among object path.")
    private boolean verbose;

    public @Override void runInternal(final GeogigCLI cli) throws IOException {
        String ref;
        if (refList.isEmpty()) {
            ref = null;
        } else {
            ref = refList.get(0);
        }

        final Function<Object, CharSequence> printFunctor = verbose ? VERBOSE_FORMATTER : FORMATTER;

        Console console = cli.getConsole();
        Listener listener = new PrintListener(console, printFunctor);
        try {
            cli.getGeogig().command(WalkGraphOp.class).setReference(ref).setListener(listener)
                    .call();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandFailedException(e.getMessage(), true);
        } finally {
            console.flush();
        }
    }

    private static class PrintListener implements WalkGraphOp.Listener {

        private final Console console;

        private final Function<Object, CharSequence> printFunctor;

        public PrintListener(Console console, Function<Object, CharSequence> printFunctor) {
            this.console = console;
            this.printFunctor = printFunctor;
        }

        private void print(Object b) {
            try {
                CharSequence line = printFunctor.apply(b);
                console.println(line);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public @Override void starTree(NodeRef treeNode) {
            print(treeNode);
        }

        public @Override void feature(NodeRef featureNode) {
            print(featureNode);
        }

        public @Override void endTree(NodeRef treeNode) {

        }

        public @Override void bucket(BucketIndex bucketIndex, Bucket bucket) {
            print(bucket);
        }

        public @Override void endBucket(BucketIndex bucketIndex, Bucket bucket) {
        }

        public @Override void commit(RevCommit commit) {
            print(commit);
        }

        public @Override void featureType(RevFeatureType ftype) {
            print(ftype);
        }

    };

    private static final Function<Object, CharSequence> FORMATTER = new Function<Object, CharSequence>() {

        /**
         * @param o a {@link Node}, {@link Bucket}, or {@link RevObject}
         */
        public @Override CharSequence apply(Object o) {
            ObjectId id;
            String type;
            if (o instanceof Bounded) {
                Bounded b = (Bounded) o;
                id = b.getObjectId();
                if (b instanceof NodeRef) {
                    type = ((NodeRef) b).getType().toString();
                } else {
                    type = "BUCKET";
                }
            } else if (o instanceof RevObject) {
                id = ((RevObject) o).getId();
                type = ((RevObject) o).getType().toString();
            } else {
                throw new IllegalArgumentException();
            }
            return String.format("%s: %s", id, type);
        }
    };

    private static final Function<Object, CharSequence> VERBOSE_FORMATTER = new Function<Object, CharSequence>() {

        /**
         * @param o a {@link Node}, {@link Bucket}, or {@link RevObject}
         */
        public @Override CharSequence apply(Object o) {
            ObjectId id;
            String type;
            String extraData = "";
            if (o instanceof Bounded) {
                Bounded b = (Bounded) o;
                id = b.getObjectId();
                if (b instanceof NodeRef) {
                    NodeRef node = (NodeRef) b;
                    type = node.getType().toString();
                    extraData = node.path();
                    if (!node.getMetadataId().isNull()) {
                        extraData += " [" + node.getMetadataId() + "]";
                    }
                } else {
                    type = "BUCKET";
                }
            } else if (o instanceof RevObject) {
                id = ((RevObject) o).getId();
                type = ((RevObject) o).getType().toString();
            } else {
                throw new IllegalArgumentException();
            }
            return String.format("%s: %s %s", id, type, extraData);
        }
    };
}
