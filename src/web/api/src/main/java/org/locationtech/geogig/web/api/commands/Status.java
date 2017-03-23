/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Web version of the Status operation in GeoGig's CLI. Lists the current branch as well as the
 * current staged and unstaged changes.
 * 
 * Web implementation of {@link Status}
 */
public class Status extends AbstractWebAPICommand {

    int offset = 0;

    int limit = -1;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setLimit(parseInt(options, "limit", 50));
        setOffset(parseInt(options, "offset", 0));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the offset variable
     * 
     * @param offset - the offset to start listing staged and unstaged changes
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Mutator for the limit variable
     * 
     * @param limit - the number of staged and unstaged changes to make
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Runs the command builds the appropriate command
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        final String pathFilter = null;
        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter writer) throws Exception {
                writer.start();
                if (!currHead.isPresent()) {
                    writer.writeErrors("Repository has no HEAD.");
                } else {
                    if (currHead.get() instanceof SymRef) {
                        final SymRef headRef = (SymRef) currHead.get();
                        writer.writeHeaderElements("branch", Ref.localName(headRef.getTarget()));
                    }
                }

                writer.writeStaged(geogig.command(DiffIndex.class).addFilter(pathFilter), offset,
                        limit);
                writer.writeUnstaged(geogig.command(DiffWorkTree.class).setFilter(pathFilter),
                        offset, limit);
                writer.writeUnmerged(geogig.command(ConflictsQueryOp.class).call(), offset, limit);

                writer.finish();
            }
        });

    }

}
