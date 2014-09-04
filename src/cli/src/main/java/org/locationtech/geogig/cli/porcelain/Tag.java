/* Copyright (c) 2013-2014 Boundless and others.
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
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.api.porcelain.TagListOp;
import org.locationtech.geogig.api.porcelain.TagRemoveOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.StagingDatabaseReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Creates or deletes tags Usage:
 * <ul>
 * <li> {@code geogig commit <tagname> [tag_commit] [-d] [-m <msg>]}
 * </ul>
 * 
 * @see TagOp
 */
@StagingDatabaseReadOnly
@Parameters(commandNames = "tag", commandDescription = "creates/deletes tags")
public class Tag extends AbstractCommand implements CLICommand {

    @Parameter(names = "-m", description = "Tag message")
    private String message;

    @Parameter(names = "-d", description = "Delete tag")
    private boolean delete;

    @Parameter(description = "<tag_name> [tag_commit]")
    private List<String> nameAndCommit = Lists.newArrayList();

    /**
     * Executes the commit command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter((message != null && !message.trim().isEmpty()) || nameAndCommit.isEmpty()
                || delete, "No tag message provided");
        checkParameter(nameAndCommit.size() < 2 || (nameAndCommit.size() == 2 && !delete),
                "Too many parameters provided");

        if (nameAndCommit.isEmpty()) {
            // looks like an attempt to create a tag with a message but forgot the tag name
            checkParameter(message == null, "A tag name must be provided");
            listTags(cli);
            return;
        }

        String name = nameAndCommit.get(0);
        String commit = nameAndCommit.size() > 1 ? nameAndCommit.get(1) : Ref.HEAD;

        ConsoleReader console = cli.getConsole();

        final GeoGIG geogig = cli.getGeogig();

        if (delete) {
            geogig.command(TagRemoveOp.class).setName(name).call();
            console.println("Deleted tag " + name);
        } else {
            Optional<ObjectId> commitId = geogig.command(RevParse.class).setRefSpec(commit).call();
            checkParameter(commitId.isPresent(), "Wrong reference: " + commit);
            RevTag tag = geogig.command(TagCreateOp.class).setName(name).setMessage(message)
                    .setCommitId(commitId.get()).call();
            console.println("Created tag " + name + " -> " + tag.getCommitId());
        }

    }

    private void listTags(GeogigCLI cli) {

        GeoGIG geogig = cli.getGeogig();
        ImmutableList<RevTag> tags = geogig.command(TagListOp.class).call();
        for (RevTag tag : tags) {
            try {
                cli.getConsole().println(tag.getName());
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

    }
}
