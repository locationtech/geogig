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

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the UpdateRef operation in the GeoGig.
 * 
 * Web interface for {@link UpdateRef}, {@link UpdateSymRef}
 */

public class UpdateRefWeb extends AbstractWebAPICommand {

    private String name;

    private String newValue;

    private boolean delete;

    /**
     * Mutator for the name variable
     * 
     * @param name - the name of the ref to update
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Mutator for the newValue variable
     * 
     * @param newValue - the new value to change the ref to
     */
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    /**
     * Mutator for the delete variable
     * 
     * @param delete - true to delete the ref
     */
    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        if (name == null) {
            throw new CommandSpecException("No name was given.");
        } else if (!(delete) && newValue == null) {
            throw new CommandSpecException(
                    "Nothing specified to update with, must specify either deletion or new value to update to.");
        }

        final Context geogig = this.getCommandLocator(context);
        Optional<Ref> ref;

        try {
            ref = geogig.command(RefParse.class).setName(name).call();

            if (!ref.isPresent()) {
                throw new CommandSpecException("Invalid name: " + name);
            }

            if (ref.get() instanceof SymRef) {
                Optional<Ref> target = geogig.command(RefParse.class).setName(newValue).call();
                if (target.isPresent() && !(target.get() instanceof SymRef)) {
                    ref = geogig.command(UpdateSymRef.class).setDelete(delete).setName(name)
                            .setNewValue(target.get().getName()).call();
                } else {
                    throw new CommandSpecException("Invalid new target: " + newValue);
                }

            } else {
                Optional<ObjectId> target = geogig.command(RevParse.class).setRefSpec(newValue)
                        .call();
                if (target.isPresent()) {
                    ref = geogig.command(UpdateRef.class).setDelete(delete)
                            .setName(ref.get().getName()).setNewValue(target.get()).call();
                } else {
                    throw new CommandSpecException("Invalid new value: " + newValue);
                }
            }
        } catch (Exception e) {
            context.setResponseContent(CommandResponse.error("Aborting UpdateRef: "
                    + e.getMessage()));
            return;
        }

        if (ref.isPresent()) {
            final Ref newRef = ref.get();
            context.setResponseContent(new CommandResponse() {

                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeUpdateRefResponse(newRef);
                    out.finish();
                }
            });
        }

    }

}
