/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Diff operation in GeoGig.
 * 
 * Web interface for {@link DiffOp}
 */

public class Diff extends AbstractWebAPICommand {
    String oldRefSpec;

    String newRefSpec;

    String pathFilter;

    boolean showGeometryChanges = false;

    int page;

    int elementsPerPage;

    public Diff(ParameterSet options) {
        super(options);
        setOldRefSpec(options.getFirstValue("oldRefSpec", null));
        setNewRefSpec(options.getFirstValue("newRefSpec", null));
        setPathFilter(options.getFirstValue("pathFilter", null));
        setShowGeometryChanges(Boolean.parseBoolean(options.getFirstValue("showGeometryChanges",
                "false")));
        setPage(parseInt(options, "page", 0));
        setElementsPerPage(parseInt(options, "show", 30));
    }

    /**
     * Mutator for the oldRefSpec variable
     * 
     * @param oldRefSpec - the old ref spec to diff against
     */
    public void setOldRefSpec(String oldRefSpec) {
        this.oldRefSpec = oldRefSpec;
    }

    /**
     * Mutator for the newRefSpec variable
     * 
     * @param newRefSpec - the new ref spec to diff against
     */
    public void setNewRefSpec(String newRefSpec) {
        this.newRefSpec = newRefSpec;
    }

    /**
     * Mutator for the pathFilter variable
     * 
     * @param pathFilter - a path to filter the diff by
     */
    public void setPathFilter(String pathFilter) {
        this.pathFilter = pathFilter;
    }

    /**
     * Mutator for only displaying geometry changes.
     * 
     * @param showGeometryChanges whether or not to only display geometry changes
     */
    public void setShowGeometryChanges(boolean showGeometryChanges) {
        this.showGeometryChanges = showGeometryChanges;
    }

    /**
     * Mutator for the page variable
     * 
     * @param page - the page number to build the response
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * Mutator for the elementsPerPage variable
     * 
     * @param elementsPerPage - the number of elements to display in the response per page
     */
    public void setElementsPerPage(int elementsPerPage) {
        this.elementsPerPage = elementsPerPage;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        if (oldRefSpec == null || oldRefSpec.trim().isEmpty()) {
            throw new CommandSpecException("No old ref spec");
        }

        final Context geogig = this.getCommandLocator(context);

        final AutoCloseableIterator<DiffEntry> diff = geogig.command(DiffOp.class)
                .setOldVersion(oldRefSpec).setNewVersion(newRefSpec).setFilter(pathFilter).call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                if (showGeometryChanges) {
                    out.writeGeometryChanges(geogig, diff, page, elementsPerPage);
                } else {
                    out.writeDiffEntries("diff", page * elementsPerPage, elementsPerPage, diff);
                }
                out.finish();
            }

            @Override
            public void close() {
                diff.close();
            }
        });
    }
}
