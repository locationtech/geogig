/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.dsl;

import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.CommandFactory;
import org.locationtech.geogig.repository.Context;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Geogig implements CommandFactory {

    private final @NonNull @Getter Context context;

    public static Geogig of(@NonNull Context repo) {
        return new Geogig(repo);
    }

    // public static Geogig of(@NonNull Repository repo) {
    // return new Geogig(repo.context());
    // }

    public @Override <T extends Command<?>> T command(@NonNull Class<T> commandClass) {
        return context.command(commandClass);
    }

    public Refs refs() {
        return new Refs(context);
    }

    public Config config() {
        return new Config(context);
    }

    public Objects objects() {
        return new Objects(context);
    }

    public Indexes indexes() {
        return new Indexes(context);
    }

    public CommitGraph graph() {
        return new CommitGraph(context);
    }

    public TreeWorker head() {
        return objects().head();
    }

    public TreeWorker workHead() {
        return objects().workHead();
    }

    public TreeWorker stageHead() {
        return objects().stageHead();
    }
}
