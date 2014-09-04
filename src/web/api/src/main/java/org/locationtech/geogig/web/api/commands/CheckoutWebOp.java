/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * The interface for the Checkout operation in GeoGig.
 * 
 * Web interface for {@link CheckoutOp}
 */

public class CheckoutWebOp extends AbstractWebAPICommand {

    private String branchOrCommit;

    private boolean ours;

    private boolean theirs;

    private String path;

    /**
     * Mutator for the branchOrCommit variable
     * 
     * @param branchOrCommit - the branch or commit to checkout
     */
    public void setName(String branchOrCommit) {
        this.branchOrCommit = branchOrCommit;
    }

    /**
     * Mutator for the ours variable
     * 
     * @param ours - true to use our version of the feature specified
     */
    public void setOurs(boolean ours) {
        this.ours = ours;
    }

    /**
     * Mutator for the theirs variable
     * 
     * @param theirs - true to use their version of the feature specified
     */
    public void setTheirs(boolean theirs) {
        this.theirs = theirs;
    }

    /**
     * Mutator for the path variable
     * 
     * @param path - the path to the feature that will be updated
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, checkout requires a transaction to preserve the stability of the repository.");
        }

        final Context geogig = this.getCommandLocator(context);
        CheckoutOp command = geogig.command(CheckoutOp.class);
        if (branchOrCommit != null) {
            Optional<Ref> head = geogig.command(RefParse.class).setName(Ref.HEAD).call();

            if (!head.isPresent()) {
                throw new CommandSpecException("Repository has no HEAD, can't merge.");
            }

            final String target = ((SymRef) head.get()).getTarget();
            command.setSource(branchOrCommit).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeElement("OldTarget", target);
                    out.writeElement("NewTarget", branchOrCommit);
                    out.finish();
                }
            });
        } else if (path != null) {
            command.addPath(path);
            if (ours && !theirs) {
                command.setOurs(ours);
            } else if (theirs && !ours) {
                command.setTheirs(theirs);
            } else {
                throw new CommandSpecException(
                        "Please specify either ours or theirs to update the feature path specified.");
            }
            command.call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeElement("Path", path);
                    out.writeElement("Strategy", ours ? "ours" : "theirs");
                    out.finish();
                }
            });
        } else {
            throw new CommandSpecException("No branch or commit specified for checkout.");
        }

    }
}
