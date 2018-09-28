/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - pulled off from GuiceContext inner class
 */
package org.locationtech.geogig.di;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;

public class DelegatingContext implements Context {

    protected Context context;

    public DelegatingContext(Context context) {
        this.context = context;
    }

    public @Override <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        T command = context.command(commandClass);
        command.setContext(this);
        return command;
    }

    public @Override WorkingTree workingTree() {
        return new WorkingTreeImpl(this);
    }

    public @Override StagingArea index() {
        return stagingArea();
    }

    public @Override StagingArea stagingArea() {
        return new StagingAreaImpl(this);
    }

    public @Override RefDatabase refDatabase() {
        return context.refDatabase();
    }

    public @Override Context snapshot() {
        return new SnapshotContext(this);
    }

    public @Override Platform platform() {
        return context.platform();
    }

    public @Override ObjectDatabase objectDatabase() {
        return context.objectDatabase();
    }

    public @Override IndexDatabase indexDatabase() {
        return context.indexDatabase();
    }

    public @Override ConflictsDatabase conflictsDatabase() {
        return context.conflictsDatabase();
    }

    public @Override ConfigDatabase configDatabase() {
        return context.configDatabase();
    }

    public @Override GraphDatabase graphDatabase() {
        return context.graphDatabase();
    }

    public @Override Repository repository() {
        return context.repository();
    }

    public @Override BlobStore blobStore() {
        return context.blobStore();
    }

    public @Override PluginDefaults pluginDefaults() {
        return context.pluginDefaults();
    }
}