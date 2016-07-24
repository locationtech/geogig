/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

public class EnvironmentBuilder implements Provider<Environment> {

    private Platform platform;

    private String[] path;

    private File absolutePath;

    private boolean stagingDatabase;

    private EnvironmentConfig forceConfig;

    private boolean readOnly;

    private Hints hints;

    @Inject
    public EnvironmentBuilder(Platform platform, @Nullable Hints hints) {
        this.platform = platform;
        this.hints = hints;
    }

    public EnvironmentBuilder setRelativePath(String... path) {
        this.path = path;
        this.absolutePath = null;
        return this;
    }

    public EnvironmentBuilder setAbsolutePath(File absolutePath) {
        this.absolutePath = absolutePath;
        this.path = null;
        return this;
    }

    File getGeoGigDirectory() {
        final Optional<URI> repoUrl = new ResolveGeogigURI(platform, hints).call();
        if (!repoUrl.isPresent()) {
            throw new IllegalStateException("Can't find geogig repository home");
        }
        URI uri = repoUrl.get();
        Preconditions.checkState("file".equals(uri.getScheme()),
                "Can't create BDB JE Environment on a non file repository URI: %s", uri);
        return new File(uri);
    }

    /**
     * @return
     * @see com.google.inject.Provider#get()
     */
    @Override
    public synchronized Environment get() {

        final Optional<URI> repoUrl = new ResolveGeogigURI(platform, hints).call();
        if (!repoUrl.isPresent() && absolutePath == null) {
            throw new IllegalStateException("Can't find geogig repository home");
        }
        final File storeDirectory;

        if (absolutePath != null) {
            storeDirectory = absolutePath;
        } else {
            URI uri = repoUrl.get();
            Preconditions.checkState("file".equals(uri.getScheme()),
                    "Can't create BDB JE Environment on a non file repository URI: %s", uri);
            File currDir = new File(uri);
            File dir = currDir;
            for (String subdir : path) {
                dir = new File(dir, subdir);
            }
            storeDirectory = dir;
        }

        if (!storeDirectory.exists() && !storeDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create Environment directory: '"
                    + storeDirectory.getAbsolutePath() + "'");
        }

        EnvironmentConfig envCfg;
        if (this.forceConfig == null) {
            File conf = new File(storeDirectory, "je.properties");
            if (!conf.exists()) {
                String resource = stagingDatabase ? "je.properties.staging"
                        : "je.properties.objectdb";
                ByteSource from = Resources.asByteSource((getClass().getResource(resource)));
                try {
                    from.copyTo(Files.asByteSink(conf));
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }

            // use the default settings
            envCfg = new EnvironmentConfig();
            envCfg.setAllowCreate(true);
            envCfg.setCacheMode(CacheMode.MAKE_COLD);
            envCfg.setLockTimeout(5, TimeUnit.SECONDS);
            envCfg.setDurability(Durability.COMMIT_SYNC);
            // envCfg.setReadOnly(readOnly);
        } else {
            envCfg = this.forceConfig;
        }

        // // envCfg.setSharedCache(true);
        // //
        // final boolean transactional = false;
        // envCfg.setTransactional(transactional);
        // envCfg.setCachePercent(75);// Use up to 50% of the heap size for the shared db cache
        // envCfg.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String.valueOf(256 * 1024 * 1024));
        // // check <http://www.oracle.com/technetwork/database/berkeleydb/je-faq-096044.html#35>
        // envCfg.setConfigParam("je.evictor.lruOnly", "false");
        // envCfg.setConfigParam("je.evictor.nodesPerScan", "100");
        //
        // envCfg.setConfigParam(EnvironmentConfig.CLEANER_MIN_UTILIZATION, "25");
        // envCfg.setConfigParam(EnvironmentConfig.CHECKPOINTER_HIGH_PRIORITY, "true");
        //
        // envCfg.setConfigParam(EnvironmentConfig.CLEANER_THREADS, "4");
        // // TODO: check whether we can set is locking to false
        // envCfg.setConfigParam(EnvironmentConfig.ENV_IS_LOCKING, String.valueOf(transactional));
        //
        // envCfg.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER,
        // String.valueOf(!transactional));
        // envCfg.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, String.valueOf(!transactional));
        //
        // // envCfg.setConfigParam(EnvironmentConfig.ENV_RUN_EVICTOR, "false");

        Environment env;
        try {
            env = new Environment(storeDirectory, envCfg);
        } catch (RuntimeException lockedEx) {
            // lockedEx.printStackTrace();
            if (readOnly) {
                // this happens when trying to open the env in read only mode when its already open
                // in read/write mode inside the same process. So we re-open it read-write but the
                // database itself will be open read-only by JEObjectDatabase.
                envCfg.setReadOnly(true);
                env = new Environment(storeDirectory, envCfg);
            } else {
                throw lockedEx;
            }
        }
        return env;
    }

    public void setIsStagingDatabase(boolean stagingDatabase) {
        this.stagingDatabase = stagingDatabase;
    }

    public void setConfig(EnvironmentConfig envCfg) {
        this.forceConfig = envCfg;
    }

    public EnvironmentBuilder setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

}
