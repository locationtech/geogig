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

import java.util.Iterator;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Ls-Tree operation in GeoGig
 * 
 * Web interface for {@link LsTreeOp}
 */
public class LsTree extends AbstractWebAPICommand {

    boolean includeTrees;

    boolean onlyTrees;

    boolean recursive;

    boolean verbose;

    String ref;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setIncludeTrees(Boolean.valueOf(options.getFirstValue("showTree", "false")));
        setOnlyTrees(Boolean.valueOf(options.getFirstValue("onlyTree", "false")));
        setRecursive(Boolean.valueOf(options.getFirstValue("recursive", "false")));
        setVerbose(Boolean.valueOf(options.getFirstValue("verbose", "false")));
        setRef(options.getFirstValue("path", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the includeTrees variable
     * 
     * @param includeTrees - true to display trees in the response
     */
    public void setIncludeTrees(boolean includeTrees) {
        this.includeTrees = includeTrees;
    }

    /**
     * Mutator for the onlyTrees variable
     * 
     * @param onlyTrees - true to display only trees in the response
     */
    public void setOnlyTrees(boolean onlyTrees) {
        this.onlyTrees = onlyTrees;
    }

    /**
     * Mutator for the recursive variable
     * 
     * @param recursive - true to recurse through the trees
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Mutator for the verbose variable
     * 
     * @param verbose - true to print out the type, metadataId and Id of the object
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Mutator for the ref variable
     * 
     * @param ref - reference to start at
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final LsTreeOp.Strategy lsStrategy;
        if (recursive) {
            if (includeTrees) {
                lsStrategy = LsTreeOp.Strategy.DEPTHFIRST;
            } else if (onlyTrees) {
                lsStrategy = LsTreeOp.Strategy.DEPTHFIRST_ONLY_TREES;
            } else {
                lsStrategy = LsTreeOp.Strategy.DEPTHFIRST_ONLY_FEATURES;
            }
        } else {
            if (onlyTrees) {
                lsStrategy = LsTreeOp.Strategy.TREES_ONLY;
            } else {
                lsStrategy = LsTreeOp.Strategy.CHILDREN;
            }
        }

        final Context geogig = this.getRepositoryContext(context);

        final Iterator<NodeRef> iter = geogig.command(LsTreeOp.class).setReference(ref)
                .setStrategy(lsStrategy).call();

        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start(true);
                out.writeLsTreeResponse(iter, verbose);
                out.finish();
            }
        });

    }

}
