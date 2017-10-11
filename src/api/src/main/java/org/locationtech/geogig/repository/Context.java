/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.annotations.Beta;

/**
 * A context object for a single repository, provides access to the different repository objects,
 * and a factory method for commands.
 * 
 * @since 1.0
 */
public interface Context extends CommandFactory {

    /**
     * @return the {@link WorkingTree} for this context
     */
    public WorkingTree workingTree();

    /**
     * @deprecated use {@link #stagingArea()} instead
     */
    public StagingArea index();

    /**
     * @return the {@link StagingArea} for this context
     */
    public StagingArea stagingArea();

    /**
     * @return the {@link RefDatabase} for this context
     */
    public RefDatabase refDatabase();

    /**
     * @return the {@link Platform} for this context
     */
    public Platform platform();

    /**
     * @return the {@link ObjectDatabase} for this context
     */
    public ObjectDatabase objectDatabase();

    /**
     * @return the {@link IndexDatabase} for this context
     */
    public IndexDatabase indexDatabase();

    /**
     * @return the {@link ConflictsDatabase} for this context
     */
    public ConflictsDatabase conflictsDatabase();

    /**
     * @return the {@link ConfigDatabase} for this context
     */
    public ConfigDatabase configDatabase();

    /**
     * @return the {@link GraphDatabase} for this context, this is a shortcut for
     *         {@link ObjectDatabase#getGraphDatabase() objectDatabase().getGraphDatabase()}
     */
    public GraphDatabase graphDatabase();

    /**
     * @return the {@link Repository} for this context
     */
    public Repository repository();

    /**
     * @return the {@link BlobStore} for this context
     */
    public BlobStore blobStore();

    /**
     * @TODO find a better way of accessing plugins and defaults. This method is currently here for
     *       the sake of {@link InitOp} and to get rid of the {@code getInstance(Class anyClass)}
     *       method in Injector
     */
    public PluginDefaults pluginDefaults();

    @Beta
    public Context snapshot();
}