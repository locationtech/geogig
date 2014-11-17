/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

/**
 * Displays a list of refs in a repository
 * 
 */
@ReadOnly
@Parameters(commandNames = "show-ref", commandDescription = "Shows a list of refs")
public class ShowRef extends AbstractCommand implements CLICommand {

    /**
     * The path to the element to display. Accepts all the notation types accepted by the RevParse
     * class
     */
    @Parameter(description = "<pattern>")
    private List<String> patterns = new ArrayList<String>();

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        ConsoleReader console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        ForEachRef op = geogig.command(ForEachRef.class);
        if (!patterns.isEmpty()) {
            Predicate<Ref> filter = new Predicate<Ref>() {
                @Override
                public boolean apply(Ref ref) {
                    for (String pattern : patterns) {
                        if (ref != null && ref.getName().endsWith("/" + pattern)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
            op.setFilter(filter);
        }
        ImmutableSet<Ref> refs = op.call();

        for (Ref ref : refs) {
            console.println(ref.getObjectId() + " " + ref.getName());
        }
    }

}
