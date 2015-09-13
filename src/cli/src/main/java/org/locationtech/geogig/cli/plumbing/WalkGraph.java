/* Copyright (c) 2013-2014 Boundless and others.
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.WalkGraphOp;
import org.locationtech.geogig.api.plumbing.WalkGraphOp.Listener;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 *
 */
@ReadOnly
@Parameters(commandNames = "walk-graph", commandDescription = "Visit objects in history graph in post order (referenced objects before referring objects)")
public class WalkGraph extends AbstractCommand implements CLICommand {

    @Parameter(description = "<[refspec]:[path]>", arity = 1)
    private List<String> refList = Lists.newArrayList();

    @Parameter(names = { "-v", "--verbose" }, description = "Verbose output, include metadata, object id, and object type among object path.")
    private boolean verbose;

    @Parameter(names = { "-i", "--indent" }, description = "Indent output.")
    private boolean indent;

    @Override
    public void runInternal(final GeogigCLI cli) throws IOException {
        String ref;
        if (refList.isEmpty()) {
            ref = null;
        } else {
            ref = refList.get(0);
        }

        AtomicInteger indentLevel = new AtomicInteger();

        final Function<Object, CharSequence> printFunctor;
        {
            Function<Object, CharSequence> formatFunctor = verbose ? VERBOSE_FORMATTER : FORMATTER;
            Function<CharSequence, CharSequence> indentFunctor = Functions.identity();
            if (indent) {
                indentFunctor = new Indenter(indentLevel);
            }
            printFunctor = Functions.compose(indentFunctor, formatFunctor);
        }
        Console console = cli.getConsole();
        Listener listener = new PrintListener(console, printFunctor, indentLevel);
        try {
            cli.getGeogig().command(WalkGraphOp.class).setReference(ref).setListener(listener)
                    .call();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandFailedException(e.getMessage(), e);
        } finally {
            console.flush();
        }
    }

    private static class PrintListener implements WalkGraphOp.Listener {

        private final Console console;

        private final Function<Object, CharSequence> printFunctor;

        private AtomicInteger indentLevel;

        public PrintListener(Console console, Function<Object, CharSequence> printFunctor,
                AtomicInteger indentLevel) {
            this.console = console;
            this.printFunctor = printFunctor;
            this.indentLevel = indentLevel;
        }

        private void print(Object b) {
            try {
                console.println(printFunctor.apply(b));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public void starTree(Node treeNode) {
            indentLevel.incrementAndGet();
            print(treeNode);
        }

        @Override
        public void feature(Node featureNode) {
            print(featureNode);
        }

        @Override
        public void endTree(Node treeNode) {
            indentLevel.decrementAndGet();
        }

        @Override
        public void bucket(int bucketIndex, int bucketDepth, Bucket bucket) {
            print(bucket);
            indentLevel.incrementAndGet();
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket bucket) {
            indentLevel.decrementAndGet();
        }

        @Override
        public void commit(RevCommit commit) {
            print(commit);
        }

        @Override
        public void featureType(RevFeatureType ftype) {
            print(ftype);
        }

    };

    private static final Function<Object, CharSequence> FORMATTER = new Function<Object, CharSequence>() {

        /**
         * @param o a {@link Node}, {@link Bucket}, or {@link RevObject}
         */
        @Override
        public CharSequence apply(Object o) {
            ObjectId id;
            String type;
            if (o instanceof Bounded) {
                Bounded b = (Bounded) o;
                id = b.getObjectId();
                if (b instanceof Node) {
                    type = ((Node) b).getType().toString();
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
        @Override
        public CharSequence apply(Object o) {
            ObjectId id;
            String type;
            String extraData = "";
            if (o instanceof Bounded) {
                Bounded b = (Bounded) o;
                id = b.getObjectId();
                if (b instanceof Node) {
                    Node node = (Node) b;
                    type = node.getType().toString();
                    extraData = node.getName();
                    if (node.getMetadataId().isPresent()) {
                        extraData += " [" + node.getMetadataId().get() + "]";
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

    private static class Indenter implements Function<CharSequence, CharSequence> {

        private AtomicInteger indentLevel;

        Indenter(AtomicInteger indentLevel) {
            this.indentLevel = indentLevel;
        }

        @Override
        public CharSequence apply(CharSequence input) {
            return new StringBuilder(Strings.repeat(" ", indentLevel.get())).append(input);
        }
    };
}
