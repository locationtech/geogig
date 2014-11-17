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
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.CatObject;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.ObjectWriter;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * The cat commands describes a repository element with no formatting at all. For a more elaborated
 * display, see the {@link show} command.
 * 
 */
@ReadOnly
@Parameters(commandNames = "cat", commandDescription = "Describes a repository element")
public class Cat extends AbstractCommand {

    /**
     * The path to the element to display. Accepts all the notation types accepted by the RevParse
     * class
     */
    @Parameter(description = "<path>")
    private List<String> paths = new ArrayList<String>();

    /**
     * Produce binary output instead of text output
     */
    @Parameter(names = { "--binary" }, description = "Produce binary output")
    private boolean binary;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(paths.size() < 2, "Only one refspec allowed");
        checkParameter(!paths.isEmpty(), "A refspec must be specified");

        ConsoleReader console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        String path = paths.get(0);

        Optional<RevObject> obj = geogig.command(RevObjectParse.class).setRefSpec(path).call();
        checkParameter(obj.isPresent(), "refspec did not resolve to any object.");
        if (binary) {
            ObjectSerializingFactory factory = DataStreamSerializationFactoryV1.INSTANCE;
            ObjectWriter<RevObject> writer = factory.createObjectWriter(obj.get().getType());
            writer.write(obj.get(), System.out);
        } else {
            CharSequence s = geogig.command(CatObject.class)
                    .setObject(Suppliers.ofInstance(obj.get())).call();
            console.println(s);
        }
    }

}
