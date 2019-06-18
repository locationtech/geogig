/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.File;
import java.net.URI;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.FileRepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.fs.FileRefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import lombok.NonNull;

public class RocksdbRepositoryResolver extends FileRepositoryResolver {

    /**
     * Format name used for configuration.
     */
    public static final String FORMAT_NAME = "rocksdb";

    /**
     * Implementation version.
     */
    public static final String VERSION = "1";

    public @Override ConfigDatabase resolveConfigDatabase(@NonNull URI repoURI,
            @NonNull Context repoContext, boolean rootUri) {
        Hints hints = new Hints().uri(repoURI);
        Platform platform = repoContext.platform();
        return new IniFileConfigDatabase(platform, hints, rootUri);
    }

    public @Override ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI, Hints hints) {
        File dbdir = new File(resolveDotGeogigDirectory(repoURI), "objects.rocksdb");
        boolean readOnly = Hints.isRepoReadOnly(hints);
        return new RocksdbObjectDatabase(dbdir, readOnly);
    }

    public @Override IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints) {
        File dbdir = new File(resolveDotGeogigDirectory(repoURI), "index.rocksdb");
        boolean readOnly = Hints.isRepoReadOnly(hints);
        return new RocksdbIndexDatabase(dbdir, readOnly);
    }

    public @Override RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints) {
        File refsdir = resolveDotGeogigDirectory(repoURI);
        boolean readOnly = Hints.isRepoReadOnly(hints);
        return new FileRefDatabase(refsdir, readOnly);
    }

    public @Override ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI, Hints hints) {
        File dbdir = new File(resolveDotGeogigDirectory(repoURI), "conflicts.rocksdb");
        dbdir.mkdir();
        return new RocksdbConflictsDatabase(dbdir);
    }
}
