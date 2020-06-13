/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import java.io.File;
import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;
import org.locationtech.geogig.storage.ConfigStore;
import org.locationtech.geogig.storage.internal.AbstractConfigDatabase;

import com.google.common.base.Preconditions;

public class IniFileConfigDatabase extends AbstractConfigDatabase {

    /**
     * Access it through {@link #local()}, not directly.
     */
    private IniFileConfigStore local;

    private IniFileConfigStore global;

    private final boolean globalOnly;

    public IniFileConfigDatabase(final Platform platform) {
        this(platform, null);
    }

    public IniFileConfigDatabase(final Platform platform, final Hints hints) {
        this(platform, hints, false);
    }

    public IniFileConfigDatabase(final Platform platform, final Hints hints,
            final boolean globalOnly) {
        super(Hints.isRepoReadOnly(hints));
        this.globalOnly = globalOnly;
        Supplier<File> globalFile = () -> {
            File home = platform.getUserHome();
            if (home == null) {
                throw new ConfigException(StatusCode.USERHOME_NOT_SET);
            }
            Preconditions.checkState(home.exists(), "user home does not exist: %s", home);
            Preconditions.checkState(home.isDirectory(), "user home is not a directory: %s", home);
            File globalConfig = new File(home, ".geogigconfig");
            return globalConfig;
        };

        global = new IniFileConfigStore(globalFile, () -> false, () -> true);
        if (globalOnly) {
            local = null;
        } else {
            Supplier<File> localFile = () -> {
                final Optional<URI> repoURI = new ResolveGeogigURI(platform, hints).call();
                if (!repoURI.isPresent()) {
                    throw new ConfigException(StatusCode.INVALID_LOCATION);
                }
                URI uri = repoURI.get();
                Preconditions.checkState("file".equals(uri.getScheme()));
                File repoDirectory = new File(uri);
                return new File(repoDirectory, "config");
            };
            local = new IniFileConfigStore(localFile, this::isReadOnly, () -> true);
        }
    }

    protected @Override ConfigStore local() {
        if (this.globalOnly) {
            throw new ConfigException(StatusCode.INVALID_LOCATION);
        }
        return this.local;
    }

    protected @Override ConfigStore global() {
        return this.global;
    }

    /**
     * @return a file config database that only supports global operations against
     *         {@code $HOME/.geogigconfig}
     */
    public static ConfigDatabase globalOnly(Platform platform) {
        return new IniFileConfigDatabase(platform, null, true);
    }
}
