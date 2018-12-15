/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.remote.http;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

/**
 * 
 * Hooks into the {@link RemoteAddOp} post call to save the password encrypted (({@code RemoteAddOp}
 * doesn't save it at all)
 *
 */
public class HttpRemotePasswordHook implements CommandHook {

    public @Override boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return RemoteAddOp.class.isAssignableFrom(clazz);
    }

    public @Override <C extends AbstractGeoGigOp<?>> C pre(C command) {
        return command;
    }

    @SuppressWarnings("unchecked")
    public @Override <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {
        if (exception == null) {
            RemoteAddOp cmd = (RemoteAddOp) command;
            String pwd = cmd.getPassword();
            if (pwd != null) {
                String configSection = "remote." + cmd.getName();
                pwd = HttpRemoteResolver.encryptPassword(pwd);
                cmd.context().configDatabase().put(configSection + ".password", pwd);
            }
        }
        return (T) retVal;
    }

}
