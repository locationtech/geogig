/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.YELLOW;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.fusesource.jansi.Ansi;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.BlameException;
import org.locationtech.geogig.porcelain.BlameOp;
import org.locationtech.geogig.porcelain.BlameReport;
import org.locationtech.geogig.porcelain.ValueAndCommit;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.text.TextValueSerializer;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;

/**
 * Shows information about the commits and authors that have modified the current attributes of a
 * given feature
 * 
 */
@ReadOnly
@Parameters(commandNames = "blame", commandDescription = "Shows information about authors of modifications for a single feature")
public class Blame extends AbstractCommand {

    /**
     * The path to the element to analyze.
     */
    @Parameter(description = "<path>")
    private List<String> paths = new ArrayList<String>();

    @Parameter(names = { "--porcelain" }, description = "Use porcelain output format")
    private boolean porcelain = false;

    @Parameter(names = { "--no-values" }, description = "Do not show values, only attribute names")
    private boolean noValues = false;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(paths.size() < 2, "Only one path allowed");
        checkParameter(!paths.isEmpty(), "A path must be specified");

        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        String path = paths.get(0);

        try {
            BlameReport report = geogig.command(BlameOp.class).setPath(path).call();

            Map<String, ValueAndCommit> changes = report.getChanges();
            Iterator<String> iter = changes.keySet().iterator();
            while (iter.hasNext()) {
                String attrib = iter.next();
                ValueAndCommit valueAndCommit = changes.get(attrib);
                RevCommit commit = valueAndCommit.commit;
                Optional<?> value = valueAndCommit.value;
                if (porcelain) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(attrib).append(' ');
                    sb.append(commit.getId().toString()).append(' ');
                    sb.append(commit.getAuthor().getName().or("")).append(' ');
                    sb.append(commit.getAuthor().getEmail().or("")).append(' ');
                    sb.append(Long.toString(commit.getAuthor().getTimestamp())).append(' ');
                    sb.append(Integer.toString(commit.getAuthor().getTimeZoneOffset()));
                    if (!noValues) {
                        sb.append(" ").append(
                                TextValueSerializer.asString(Optional.of((Object) value.orNull())));
                    }
                    console.println(sb.toString());
                } else {
                    Ansi ansi = newAnsi(console);
                    ansi.fg(GREEN).a(attrib + ": ").reset();
                    if (!noValues) {
                        String s = value.isPresent() ? value.get().toString() : "NULL";
                        ansi.fg(YELLOW).a(s).a(" ").reset();
                    }
                    ansi.a(commit.getId().toString().substring(0, 8)).a(" ");
                    ansi.a(commit.getAuthor().getName().or("")).a(" ");
                    ansi.a(commit.getAuthor().getEmail().or("")).a(" ");
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                    String date = formatter.format(new Date(commit.getAuthor().getTimestamp()
                            + commit.getAuthor().getTimeZoneOffset()));
                    ansi.a(date);
                    console.println(ansi.toString());
                }
            }
        } catch (BlameException e) {
            switch (e.statusCode) {
            case FEATURE_NOT_FOUND:
                throw new InvalidParameterException("The supplied path does not exist", e);
            case PATH_NOT_FEATURE:
                throw new InvalidParameterException(
                        "The supplied path does not resolve to a feature", e);

            }
        }
    }
}
