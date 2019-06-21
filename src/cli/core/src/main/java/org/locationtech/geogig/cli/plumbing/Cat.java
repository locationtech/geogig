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
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.CatObject;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;

import com.google.common.base.Suppliers;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The cat commands describes a repository element with no formatting at all. For a more elaborated
 * display, see the {@link show} command.
 * 
 */
@ReadOnly
@Command(name = "cat", description = "Describes a repository element")
public class Cat extends AbstractCommand {

    /**
     * The path to the element to display. Accepts all the notation types accepted by the RevParse
     * class
     */
    @Parameters(description = "<path>")
    private List<String> path = new ArrayList<String>();

    /**
     * Produce binary output instead of text output
     */
    @Option(names = { "--binary" }, description = "Produce binary output")
    private boolean binary;

    public @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(path.size() < 2, "Only one refspec allowed");
        checkParameter(!path.isEmpty(), "A refspec must be specified");

        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        String spath = path.get(0);

        Optional<RevObject> obj;
        RevObjectParse cmd = geogig.command(RevObjectParse.class).setRefSpec(spath);
        obj = cmd.call();
        if (!obj.isPresent()) {
            obj = cmd.setSource(geogig.getContext().indexDatabase()).call();
        }
        checkParameter(obj.isPresent(), "refspec did not resolve to any object.");
        if (binary) {
            RevObjectSerializer factory = DataStreamRevObjectSerializerV1.INSTANCE;
            factory.write(obj.get(), System.out);
        } else {
            CharSequence s = geogig.command(CatObject.class)
                    .setObject(Suppliers.ofInstance(obj.get())).call();
            console.println(s);
        }
    }

}
