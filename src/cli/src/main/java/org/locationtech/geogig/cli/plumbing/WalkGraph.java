/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.CreateDeduplicator;
import org.locationtech.geogig.api.plumbing.WalkGraphOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.storage.Deduplicator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
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

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        String ref;
        if (refList.isEmpty()) {
            ref = null;
        } else {
            ref = refList.get(0);
        }
        Deduplicator deduplicator = cli.getGeogig().command(CreateDeduplicator.class).call();
        try {
            Iterator<RevObject> iter = cli.getGeogig() //
                    .command(WalkGraphOp.class).setReference(ref) //
                    .setDeduplicator(deduplicator) //
                    // .setStrategy(lsStrategy) //
                    .call();

            final ConsoleReader console = cli.getConsole();
            if (!iter.hasNext()) {
                if (ref == null) {
                    console.println("The working tree is empty");
                } else {
                    console.println("The specified path is empty");
                }
                return;
            }

            Function<RevObject, CharSequence> printFunctor = new Function<RevObject, CharSequence>() {
                @Override
                public CharSequence apply(RevObject input) {
                    if (verbose) {
                        return String.format("%s: %s %s", input.getId(), input.getType(), input);
                    } else {
                        return String.format("%s: %s", input.getId(), input.getType());
                    }
                }
            };

            Iterator<CharSequence> lines = Iterators.transform(iter, printFunctor);

            while (lines.hasNext()) {
                console.println(lines.next());
            }
            console.flush();
        } finally {
            deduplicator.release();
        }
    }
}
