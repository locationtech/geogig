/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal.coordcache;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.Random;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequence;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequenceFactory;
import org.locationtech.geogig.storage.bdbje.EnvironmentBuilder;

import com.google.common.base.Preconditions;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.vividsolutions.jts.geom.CoordinateSequence;

/**
 * A {@link PointCache} that uses a temporary BDB JE database inside the repository's
 * {@code .geogig/tmp}
 */
public class BDBJEPointCache implements PointCache {

    private static final OSMCoordinateSequenceFactory CSFAC = OSMCoordinateSequenceFactory
            .instance();

    private static final Random random = new Random();

    private Environment environment;

    private Database database;

    public BDBJEPointCache(Platform platform) {
        String envName = "tmpPointCache_" + Math.abs(random.nextInt());

        EnvironmentConfig envCfg;
        envCfg = new EnvironmentConfig();
        envCfg.setAllowCreate(true);
        envCfg.setTransactional(false);

        envCfg.setSharedCache(true);
        envCfg.setCacheMode(CacheMode.MAKE_COLD);

        envCfg.setDurability(Durability.COMMIT_NO_SYNC);
        envCfg.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String.valueOf(1024 * 1024 * 1024));
        envCfg.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
        envCfg.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false");
        envCfg.setConfigParam("je.evictor.lruOnly", "false");
        envCfg.setConfigParam("je.evictor.nodesPerScan", "1000");

        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder(platform);
        environmentBuilder.setRelativePath("tmp", "pointcache", envName);
        environmentBuilder.setIsStagingDatabase(true);
        environmentBuilder.setConfig(envCfg);

        this.environment = environmentBuilder.get();

        DatabaseConfig dbc = new DatabaseConfig();
        dbc.setAllowCreate(true);
        dbc.setTemporary(true);
        this.database = this.environment.openDatabase(null, "pointcache", dbc);
    }

    @Override
    public void put(Long nodeId, OSMCoordinateSequence coord) {
        Preconditions.checkNotNull(nodeId, "id is null");
        Preconditions.checkNotNull(coord, "coord is null");
        Preconditions.checkArgument(1 == coord.size(), "coord list size is not 1");

        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(nodeId.longValue(), key);

        int[] c = coord.ordinates();
        DatabaseEntry data = CoordinateBinding.objectToEntry(c);

        database.put(null, key, data);
    }

    @Override
    public synchronized void dispose() {
        if (environment == null) {
            return;
        }
        final File envHome = environment.getHome();
        try {
            database.close();
        } catch (RuntimeException e) {
            throw new RuntimeException("Error closing point cache", e);
        } finally {
            database = null;
            try {
                environment.close();
            } finally {
                environment = null;
                envHome.listFiles(new FileFilter() {

                    @Override
                    public boolean accept(File file) {
                        return file.delete();
                    }
                });
                envHome.delete();
            }
        }
    }

    private static final class CoordinateBinding extends TupleBinding<int[]> {

        private static final CoordinateBinding INSTANCE = new CoordinateBinding();

        public static final DatabaseEntry objectToEntry(int[] coord) {
            DatabaseEntry data = new DatabaseEntry();
            INSTANCE.objectToEntry(coord, data);
            return data;
        }

        public static int[] entryToCoord(DatabaseEntry data) {
            return INSTANCE.entryToObject(data);
        }

        @Override
        public int[] entryToObject(TupleInput input) {
            return new int[] { input.readInt(), input.readInt() };
        }

        @Override
        public void objectToEntry(int[] c, TupleOutput output) {
            output.writeInt(c[0]);
            output.writeInt(c[1]);
        }

    }

    @Override
    public CoordinateSequence get(List<Long> ids) {
        Preconditions.checkNotNull(ids, "ids is null");

        OSMCoordinateSequence cs = CSFAC.create(ids.size());
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        for (int index = 0; index < ids.size(); index++) {
            Long nodeID = ids.get(index);
            LongBinding.longToEntry(nodeID.longValue(), key);
            OperationStatus status = database.get(null, key, data, LockMode.DEFAULT);
            if (!OperationStatus.SUCCESS.equals(status)) {
                String msg = String.format("node id %s not found", nodeID);
                throw new IllegalArgumentException(msg);

            }
            int[] c = CoordinateBinding.entryToCoord(data);
            cs.setOrdinate(index, 0, c[0]);
            cs.setOrdinate(index, 1, c[1]);
        }
        return cs;
    }
}
