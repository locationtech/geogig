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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public class PRFindOp extends PRCommand<Optional<PR>> {

    private @NonNull Integer id;

    public PRFindOp id(Integer id) {
        this.id = id;
        return this;
    }

    public PRFindOp setId(int id) {
        this.id = id;
        return this;
    }

    public PR getOrFail() {
        return call().orElseThrow(
                () -> new NoSuchElementException(String.format("Pull request %d not found", id)));
    }

    protected @Override Optional<PR> _call() {
        Preconditions.checkArgument(id != null, "Pull request id not set");
        final String section = String.format("pr.%d", id);
        final Map<String, String> props = configDatabase().getAllSection(section);
        if (props.isEmpty()) {
            return Optional.empty();
        }

        PR pr = PR.createFromProperties(id, props);

        return Optional.of(pr);
    }

}
