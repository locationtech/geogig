/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Map;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ConfigException;

import com.google.common.base.Optional;

/**
 * Get a repository or global options
 * <p>
 * This is just a shortcut for using ConfigOp in the case of wanting to retrieve a single config
 * value
 * <p>
 * 
 * @see ConfigOp
 */
@CanRunDuringConflict
public class ConfigGet extends AbstractGeoGigOp<Optional<String>> {

    private boolean global;

    private String name;

    /**
     * Executes the config command with the specified options.
     * 
     * @return Optional<String> if querying for a value, empty Optional if no matching name was
     *         found.
     * @throws ConfigException if an error is encountered. More specific information can be found in
     *         the exception's statusCode.
     */
    @Override
    protected Optional<String> _call() {
        ConfigScope scope = global ? ConfigScope.GLOBAL : ConfigScope.LOCAL;
        Optional<Map<String, String>> configGetResult = command(ConfigOp.class)
                .setAction(ConfigAction.CONFIG_GET).setName(name).setScope(scope).call();
        if (configGetResult.isPresent()) {
            return Optional.of(configGetResult.get().get(name));
        } else {
            return Optional.absent();
        }

    }

    /**
     * @param global if true, config actions will be executed on the global configuration file. If
     *        false, then all actions will be done on the config file in the local repository.
     * @return {@code this}
     */
    public ConfigGet setGlobal(boolean global) {
        this.global = global;
        return this;
    }

    /**
     * @param name the name of the variable to get
     * @return {@code this}
     */
    public ConfigGet setName(String name) {
        this.name = name;
        return this;
    }

}