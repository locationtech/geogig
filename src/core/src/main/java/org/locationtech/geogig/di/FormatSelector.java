/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import java.util.Map;

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.VersionedFormat;

import com.google.common.base.Optional;
import com.google.inject.Provider;

abstract class FormatSelector<T> implements Provider<T> {
    private final ConfigDatabase config;

    private final Map<VersionedFormat, Provider<T>> plugins;

    public FormatSelector(ConfigDatabase config, Map<VersionedFormat, Provider<T>> plugins) {
        this.config = config;
        this.plugins = plugins;
    }

    protected abstract VersionedFormat readConfig(ConfigDatabase config);

    @Override
    final public T get() {
        try {
            VersionedFormat configuredFormat = readConfig(config);
            Provider<T> formatProvider = plugins.get(configuredFormat);
            if (formatProvider == null) {
                throw new RuntimeException(
                        "No such format: " + configuredFormat + "(from " + config.getAll() + ")");
            } else {
                return formatProvider.get();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected Optional<String> getConfig(String key, ConfigDatabase config) {
        Optional<String> val = config.get(key);
        if (!val.isPresent()) {
            val = config.getGlobal(key);
        }
        return val;
    }
}
