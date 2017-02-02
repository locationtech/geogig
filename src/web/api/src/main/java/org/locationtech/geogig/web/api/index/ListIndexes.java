/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.index;

import java.util.List;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Lists all indexes in the database, or all of the indexes on a given feature type tree.
 */
public class ListIndexes extends AbstractWebAPICommand {

    String treeName;

    public ListIndexes(ParameterSet options) {
        super(options);
        setTreeName(options.getFirstValue("treeName", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the treeName variable
     * 
     * @param tref - reference to start at
     */
    public void setTreeName(String treeName) {
        this.treeName = treeName;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {

        final Context geogig = this.getRepositoryContext(context);
        final List<IndexInfo> indexInfos;
        if (treeName != null) {
            indexInfos = geogig.indexDatabase().getIndexInfos(treeName);
        } else {
            indexInfos = geogig.indexDatabase().getIndexInfos();
        }

        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start(true);
                out.writeIndexInfos(indexInfos, "index");
                out.finish();
            }
        });

    }

}
