/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.cli;

import static java.lang.String.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.util.Converters;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.ql.porcelain.QLDelete;
import org.locationtech.geogig.ql.porcelain.QLInsert;
import org.locationtech.geogig.ql.porcelain.QLSelect;
import org.locationtech.geogig.ql.porcelain.QLUpdate;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;

@Parameters(commandNames = "ql", commandDescription = "executes a geogig query language statement")
@RequiresRepository(true)
public class QL extends AbstractCommand {

    @Parameter(required = true, arity = 1, description = "Either a SELECT, UPDATE, DELETE, or INSERT statement in geogig Query Language")
    private List<String> statement = new ArrayList<>(1);

    @Override
    protected void runInternal(final GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        final String s = statement.get(0).trim();
        checkParameter(!s.isEmpty(), "Statement not provided");

        final String c = Splitter.on(' ').limit(2).splitToList(s).get(0).toUpperCase();
        switch (c) {
        case "INSERT":
            runInsert(cli, s);
            break;
        case "UPDATE":
            runUpdate(cli, s);
            break;
        case "DELETE":
            runDelete(cli, s);
            break;
        case "SELECT":
            runSelect(cli, s);
            break;
        default:
            throw new InvalidParameterException("Unrecognized statement: " + c);
        }
    }

    private void runInsert(GeogigCLI cli, String statement) throws IOException {
        GeoGIG repo = cli.getGeogig();
        Supplier<DiffObjectCount> res = repo.command(QLInsert.class).setStatement(statement).call();
        DiffObjectCount count = res.get();
        cli.getConsole().println(format("Inserted %,d features", count.getFeaturesAdded()));
    }

    private void runUpdate(GeogigCLI cli, String statement) throws IOException {
        GeoGIG repo = cli.getGeogig();
        Supplier<DiffObjectCount> res = repo.command(QLUpdate.class).setStatement(statement).call();
        DiffObjectCount count = res.get();
        cli.getConsole().println(format("Updated %,d features", count.getFeaturesChanged()));
    }

    private void runDelete(GeogigCLI cli, String statement) throws IOException {
        GeoGIG repo = cli.getGeogig();
        Supplier<DiffObjectCount> res = repo.command(QLDelete.class).setStatement(statement).call();
        DiffObjectCount count = res.get();
        cli.getConsole().println(format("Deleted %,d features", count.getFeaturesRemoved()));
    }

    private void runSelect(GeogigCLI cli, String statement) throws IOException {
        GeoGIG geogig = cli.getGeogig();
        ProgressListener listener = cli.getProgressListener();
        SimpleFeatureCollection fcol;
        try {
            fcol = geogig.command(QLSelect.class).setStatement(statement)
                    .setProgressListener(listener).call();
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }

        Console console = cli.getConsole();

        SimpleFeatureType schema = fcol.getSchema();

        // QLSelect.BOUNDS_TYPE and COUNT_TYPE names start with @
        final boolean printFids = !schema.getTypeName().startsWith("@");

        console.println(ftformat(schema, printFids));

        long total = 0;
        try (SimpleFeatureIterator it = fcol.features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                String formatted = fformat(f, printFids);
                console.println(formatted);
                ++total;
            }
        }
        if (printFids) {
            console.println(format("total: %,d", total));
        }
    }

    private CharSequence ftformat(SimpleFeatureType schema, boolean printFids) {
        StringBuilder sb = new StringBuilder();
        if (printFids) {
            sb.append("@ID\t");
        }
        for (AttributeDescriptor o : schema.getAttributeDescriptors()) {
            sb.append(o.getLocalName()).append('\t');
        }
        return sb.toString();
    }

    private String fformat(SimpleFeature f, boolean printFids) {
        StringBuilder sb = new StringBuilder();
        if (printFids) {
            sb.append(f.getID()).append('\t');
        }
        for (Object o : f.getAttributes()) {
            sb.append(Converters.convert(o, String.class)).append('\t');
        }
        return sb.toString();
    }

}
