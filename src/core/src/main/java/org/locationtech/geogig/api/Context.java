/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

/**
 * A context object for a single repository, provides access to the different repository objects,
 * and a factory method for commands.
 */
public interface Context {

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass);

    public WorkingTree workingTree();

    /**
     * @return
     */
    public StagingArea index();

    /**
     * @return
     */
    public RefDatabase refDatabase();

    public Platform platform();

    public ObjectDatabase objectDatabase();

    public StagingDatabase stagingDatabase();

    public ConfigDatabase configDatabase();

    public GraphDatabase graphDatabase();

    /**
     * @deprecated commands should not access the repository instance but from its components as
     *             given by the other methods in this interface
     */
    @Deprecated
    public Repository repository();

    public DeduplicationService deduplicationService();

    /**
     * @TODO find a better way of accessing plugins and defaults. This method is currently here for
     *       the sake of {@link InitOp} and to get rid of the {@code getInstance(Class anyClass)}
     *       method in Injector
     */
    public PluginDefaults pluginDefaults();

}