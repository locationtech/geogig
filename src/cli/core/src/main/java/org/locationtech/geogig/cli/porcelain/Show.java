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

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.CatObject;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.text.CrsTextSerializer;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;

/**
 * Shows formatted information about a commit, tree, feature or feature type
 * 
 */
@ReadOnly
@Parameters(commandNames = "show", commandDescription = "Displays information about a commit, feature or feature type")
public class Show extends AbstractCommand implements CLICommand {

    /**
     * The path to the element to display.
     */
    @Parameter(description = "<reference>")
    private List<String> refs = new ArrayList<String>();

    @Parameter(names = { "--raw" }, description = "Produce machine-readable output")
    private boolean raw;

    /**
     * @param cli
     * @see org.locationtech.geogig.cli.CLICommand#run(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!refs.isEmpty(), "A refspec must be specified");
        if (raw) {
            printRaw(cli);
        } else {
            printFormatted(cli);
        }

    }

    private void printRaw(GeogigCLI cli) throws IOException {
        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();
        for (String ref : refs) {
            Optional<RevObject> obj = geogig.command(RevObjectParse.class).setRefSpec(ref).call();
            if (!obj.isPresent()) {
                ref = getFullRef(ref);
                obj = geogig.command(RevObjectParse.class).setRefSpec(ref).call();
            }
            checkParameter(obj.isPresent(), "refspec did not resolve to any object.");
            RevObject revObject = obj.get();
            if (revObject instanceof RevFeature) {
                Optional<RevFeatureType> opt = geogig.command(ResolveFeatureType.class)
                        .setRefSpec(ref).call();
                if (opt.isPresent()) {
                    RevFeatureType ft = opt.get();
                    ImmutableList<PropertyDescriptor> attribs = ft.descriptors();
                    RevFeature feature = (RevFeature) revObject;
                    Ansi ansi = super.newAnsi(console);
                    ansi.a(ref).newline();
                    ansi.a(feature.getId().toString()).newline();
                    for (int i = 0; i < feature.size(); i++) {
                        Optional<Object> value = feature.get(i);
                        PropertyDescriptor attrib = attribs.get(i);
                        ansi.a(attrib.getName()).newline();
                        PropertyType attrType = attrib.getType();
                        String typeName = FieldType.forBinding(attrType.getBinding()).name();
                        if (attrType instanceof GeometryType) {
                            GeometryType gt = (GeometryType) attrType;
                            CoordinateReferenceSystem crs = gt.getCoordinateReferenceSystem();
                            String crsText = CrsTextSerializer.serialize(crs);
                            ansi.a(typeName).a(" ").a(crsText).newline();
                        } else {
                            ansi.a(typeName).newline();
                        }
                        ansi.a(value.or("[NULL]").toString()).newline();
                    }
                    console.println(ansi.toString());
                } else {
                    CharSequence s = geogig.command(CatObject.class)
                            .setObject(Suppliers.ofInstance(revObject)).call();
                    console.println(s);
                }
            } else {
                CharSequence s = geogig.command(CatObject.class)
                        .setObject(Suppliers.ofInstance(revObject)).call();
                console.println(s);
            }
        }
    }

    public void printFormatted(GeogigCLI cli) throws IOException {
        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();
        for (String ref : refs) {
            Optional<RevObject> obj = geogig.command(RevObjectParse.class).setRefSpec(ref).call();
            if (!obj.isPresent()) {
                ref = getFullRef(ref);
                obj = geogig.command(RevObjectParse.class).setRefSpec(ref).call();
            }
            checkParameter(obj.isPresent(), "refspec did not resolve to any object.");
            RevObject revObject = obj.get();
            if (revObject instanceof RevFeature) {
                Optional<RevFeatureType> opt = geogig.command(ResolveFeatureType.class)
                        .setRefSpec(ref).call();
                if (opt.isPresent()) {
                    RevFeatureType ft = opt.get();
                    ImmutableList<PropertyDescriptor> attribs = ft.descriptors();
                    RevFeature feature = (RevFeature) revObject;
                    Ansi ansi = super.newAnsi(console);
                    ansi.newline().fg(Color.YELLOW).a("ID:  ").reset().a(feature.getId().toString())
                            .newline();
                    ansi.fg(Color.YELLOW).a("FEATURE TYPE ID:  ").reset().a(ft.getId().toString())
                            .newline().newline();
                    ansi.a("ATTRIBUTES  ").newline();
                    ansi.a("----------  ").newline();
                    for (int i = 0; i < feature.size(); i++) {
                        Optional<Object> value = feature.get(i);
                        ansi.fg(Color.YELLOW).a(attribs.get(i).getName() + ": ").reset();
                        ansi.a(value.or("[NULL]").toString()).newline();
                    }
                    console.println(ansi.toString());
                } else {
                    CharSequence s = geogig.command(CatObject.class)
                            .setObject(Suppliers.ofInstance(revObject)).call();
                    console.println(s);
                }

            } else if (revObject instanceof RevTree) {
                RevTree tree = (RevTree) revObject;
                Optional<RevFeatureType> opt = geogig.command(ResolveFeatureType.class)
                        .setRefSpec(ref).call();
                checkParameter(opt.isPresent(),
                        "Refspec must resolve to a commit, tree, feature or feature type");
                RevFeatureType ft = opt.get();
                Ansi ansi = super.newAnsi(console);

                ansi.fg(Color.YELLOW).a("TREE ID:  ").reset().a(tree.getId().toString()).newline();
                ansi.fg(Color.YELLOW).a("SIZE:  ").reset().a(Long.toString(tree.size())).newline();
                ansi.fg(Color.YELLOW).a("NUMBER Of SUBTREES:  ").reset()
                        .a(Integer.toString(tree.numTrees()).toString()).newline();

                printFeatureType(ansi, ft, true);

                console.println(ansi.toString());
            } else if (revObject instanceof RevCommit) {
                RevCommit commit = (RevCommit) revObject;
                Ansi ansi = super.newAnsi(console);
                ansi.a(Strings.padEnd("Commit:", 15, ' ')).fg(Color.YELLOW)
                        .a(commit.getId().toString()).reset().newline();
                ansi.a(Strings.padEnd("Author:", 15, ' ')).fg(Color.GREEN)
                        .a(formatPerson(commit.getAuthor())).reset().newline();
                ansi.a(Strings.padEnd("Committer:", 15, ' ')).fg(Color.GREEN)
                        .a(formatPerson(commit.getCommitter())).reset().newline();
                ansi.a(Strings.padEnd("Author date:", 15, ' ')).a("(").fg(Color.RED)
                        .a(estimateSince(geogig.getPlatform(), commit.getAuthor().getTimestamp()))
                        .reset().a(") ").a(new Date(commit.getAuthor().getTimestamp())).newline();
                ansi.a(Strings.padEnd("Committer date:", 15, ' ')).a("(").fg(Color.RED)
                        .a(estimateSince(geogig.getPlatform(),
                                commit.getCommitter().getTimestamp()))
                        .reset().a(") ").a(new Date(commit.getCommitter().getTimestamp()))
                        .newline();
                ansi.a(Strings.padEnd("Subject:", 15, ' ')).a(commit.getMessage()).newline();
                console.println(ansi.toString());
            } else if (revObject instanceof RevFeatureType) {
                Ansi ansi = super.newAnsi(console);
                printFeatureType(ansi, (RevFeatureType) revObject, false);
                console.println(ansi.toString());
            } else {
                throw new InvalidParameterException(
                        "Refspec must resolve to a commit, tree, feature or feature type");
            }
            console.println();
        }

    }

    /**
     * Completes a refspec in case it is just a path, assuming it refers to the working tree and
     * appending WORK_HEAD
     * 
     * @param ref the refspec
     * @return the full refspec from the passed one
     */
    private String getFullRef(String ref) {
        if (!ref.contains(":")) {
            ref = "WORK_HEAD:" + ref;
        }
        return ref;
    }

    private void printFeatureType(Ansi ansi, RevFeatureType ft, boolean useDefaultKeyword) {
        ImmutableList<PropertyDescriptor> attribs = ft.descriptors();

        ansi.fg(Color.YELLOW).a(useDefaultKeyword ? "DEFAULT " : "").a("FEATURE TYPE ID:  ").reset()
                .a(ft.getId().toString()).newline().newline();
        ansi.a(useDefaultKeyword ? "DEFAULT " : "").a("FEATURE TYPE ATTRIBUTES").newline();
        for (PropertyDescriptor attrib : attribs) {
            ansi.fg(Color.YELLOW).a(attrib.getName() + ": ").reset()
                    .a("<" + FieldType.forBinding(attrib.getType().getBinding()) + ">").newline();
        }
    }

    /**
     * Converts a RevPerson for into a readable string.
     * 
     * @param person the person to format.
     * @return the formatted string
     * @see RevPerson
     */
    private String formatPerson(RevPerson person) {
        StringBuilder sb = new StringBuilder();
        sb.append(person.getName().or("<name not set>"));
        sb.append(" <").append(person.getEmail().or("")).append('>');
        return sb.toString();
    }

    /**
     * Converts a timestamp into a readable string that represents the rough time since that
     * timestamp.
     * 
     * @param platform
     * @param timestamp
     * @return
     */
    private String estimateSince(Platform platform, long timestamp) {
        long now = platform.currentTimeMillis();
        long diff = now - timestamp;
        final long seconds = 1000;
        final long minutes = seconds * 60;
        final long hours = minutes * 60;
        final long days = hours * 24;
        final long weeks = days * 7;
        final long months = days * 30;
        final long years = days * 365;

        if (diff > years) {
            return diff / years + " years ago";
        }
        if (diff > months) {
            return diff / months + " months ago";
        }
        if (diff > weeks) {
            return diff / weeks + " weeks ago";
        }
        if (diff > days) {
            return diff / days + " days ago";
        }
        if (diff > hours) {
            return diff / hours + " hours ago";
        }
        if (diff > minutes) {
            return diff / minutes + " minutes ago";
        }
        if (diff > seconds) {
            return diff / seconds + " seconds ago";
        }
        return "just now";
    }

}
