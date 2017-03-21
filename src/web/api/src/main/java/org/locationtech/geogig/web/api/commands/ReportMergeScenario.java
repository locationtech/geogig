/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.PagedMergeScenarioConsumer;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the {@link ReportMergeScenarioOp} operation in GeoGig.
 */
public class ReportMergeScenario extends AbstractWebAPICommand {

    public static final int DEFAULT_MERGE_SCENARIO_PAGE_SIZE = 1000;

    String theirCommit;

    String ourCommit;

    int pageNumber;

    int elementsPerPage;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setTheirCommit(options.getRequiredValue("theirCommit"));
        setOurCommit(options.getRequiredValue("ourCommit"));
        setPageNumber(Integer.parseInt(options.getFirstValue("page", "0")));
        setElementsPerPage(Integer.parseInt(options.getFirstValue("elementsPerPage",
                Integer.toString(DEFAULT_MERGE_SCENARIO_PAGE_SIZE))));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the theirCommit variable
     * 
     * @param commit - the commit to merge
     */
    public void setTheirCommit(String theirCommit) {
        this.theirCommit = theirCommit;
    }

    /**
     * Mutator for the outCommit variable
     * 
     * @param commit - the commit to merge into
     */
    public void setOurCommit(String ourCommit) {
        this.ourCommit = ourCommit;
    }

    /**
     * Mutator for the pageNumber variable
     * 
     * @param pageNumber the page of results to show
     */
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    /**
     * Mutator for the elementsPerPage variable
     * 
     * @param elementsPerPage the number of features per page
     */
    public void setElementsPerPage(int elementsPerPage) {
        this.elementsPerPage = elementsPerPage;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);
        final Optional<RevCommit> ours = geogig.command(RevObjectParse.class).setRefSpec(ourCommit)
                .call(RevCommit.class);
        if (!ours.isPresent()) {
            throw new CommandSpecException(
                    "'our' commit could not be resolved to a commit object.");
        }
        final Optional<RevCommit> theirs = geogig.command(RevObjectParse.class)
                .setRefSpec(theirCommit).call(RevCommit.class);
        if (!theirs.isPresent()) {
            throw new CommandSpecException(
                    "'their' commit could not be resolved to a commit object.");
        }

        final PagedMergeScenarioConsumer consumer = new PagedMergeScenarioConsumer(pageNumber,
                elementsPerPage);

        geogig.command(ReportMergeScenarioOp.class).setMergeIntoCommit(ours.get())
                .setToMergeCommit(theirs.get()).setConsumer(consumer).call();

        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeReportMergeScenarioResponse(geogig, ours.get().getId(),
                        theirs.get().getId(), consumer);
                out.finish();
            }
        });
    }
}
