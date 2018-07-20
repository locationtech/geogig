/* Copyright (c) 2013-2016 Boundless and others.
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
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
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

        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        ForEachRef op = geogig.command(ForEachRef.class);

        Predicate<Ref> filter = new Predicate<Ref>() {
            @Override
            public boolean apply(Ref ref) {
                String name = ref.getName();
                if (!name.startsWith(Ref.REFS_PREFIX)) {
                    return false;
                }
                boolean match = patterns.isEmpty() ? true : false;
                for (String pattern : patterns) {
                    if (Strings.isNullOrEmpty(pattern)) {
                        match = true;
                    } else if (name.endsWith("/" + pattern)) {
                        match = true;
                        break;
                    }
                }
                return match;
            }
        };
        op.setFilter(filter);

        ImmutableSet<Ref> refs = op.call();

        for (Ref ref : refs) {
            console.println(ref.getObjectId() + " " + ref.getName());
        }
    }

}
