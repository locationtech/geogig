/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 * Johnathan Garrett (Prominent Edge) - delete and create tags support
 */
package org.locationtech.geogig.web.api.commands;

import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.porcelain.TagRemoveOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Optional;

/**
 * Interface for the Tag operations in GeoGig.
 * 
 * Web interface for {@link TagListOp}, {@link TagRemoveOp}, and {@link TagCreateOp}
 */

public class Tag extends AbstractWebAPICommand {

    String name;

    String commit;

    String message;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setName(options.getFirstValue("name", null));
        setCommit(options.getFirstValue("commit", null));
        setMessage(options.getFirstValue("message", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.POST.equals(method) || RequestMethod.GET.equals(method)
                || RequestMethod.DELETE.equals(method) || super.supports(method);
    }

    /**
     * Mutator for the name variable
     * 
     * @param name - the tag name to work with
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Mutator for the message variable
     * 
     * @param message - the message to use when creating new tags
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Mutator for the commit variable
     * 
     * @param commit - the commit to use when creating new tags
     */
    public void setCommit(String commit) {
        this.commit = commit;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        if (context.getMethod() == RequestMethod.GET) {
            final List<RevTag> tags = geogig.command(TagListOp.class).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTagListResponse(tags);
                    out.finish();
                }
            });
        } else if (context.getMethod() == RequestMethod.DELETE) {
            if (name == null) {
                throw new CommandSpecException("You must specify the tag name to delete.");
            }
            final RevTag removed = geogig.command(TagRemoveOp.class).setName(name).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTagDeleteResponse(removed);
                    out.finish();
                }
            });
        } else {
            if (name == null) {
                throw new CommandSpecException(
                        "You must specify list or delete, or provide a name, message, and commit for the new tag.");
            } else if (commit == null) {
                throw new CommandSpecException("You must specify a commit to point the tag to.");
            }
            Optional<ObjectId> commitId = geogig.command(RevParse.class).setRefSpec(commit).call();
            if (!commitId.isPresent()) {
                throw new CommandSpecException("'" + commit + "' could not be resolved.");
            }
            final RevTag tag = geogig.command(TagCreateOp.class).setName(name).setMessage(message)
                    .setCommitId(commitId.get()).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTagCreateResponse(tag);
                    out.finish();
                }
            });
        }
    }

}
