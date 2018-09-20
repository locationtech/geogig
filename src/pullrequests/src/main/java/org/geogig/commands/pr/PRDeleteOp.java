/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import java.util.Optional;

import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import lombok.NonNull;

public class PRDeleteOp extends PRCommand<Boolean> {

    private @NonNull Integer id;

    public PRDeleteOp setId(int id) {
        this.id = id;
        return this;
    }

    protected @Override Boolean _call() {
        Optional<PR> propt = command(PRFindOp.class).setId(id).call();
        propt.ifPresent(pr -> {
            final String section = String.format("pr.%d", id);
            configDatabase().removeSection(section);
            Context context = context();
            Optional<GeogigTransaction> tx = pr.tryGetTransaction(context);
            if (tx.isPresent()) {
                tx.get().abort();
            } else {
                delete(context, pr.getHeadRef());
                delete(context, pr.getOriginRef());
            }

        });
        return propt.isPresent();
    }

    private void delete(Context liveContext, String ref) {
        liveContext.command(UpdateRef.class).setName(ref).setDelete(true).call();
    }

}
