/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.scripting;

import java.io.File;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

class JVMScriptHook implements CommandHook {

    private File preScript;

    private File postScript;

    public JVMScriptHook(@Nullable final File preScript, @Nullable final File postScript) {
        this.preScript = preScript;
        this.postScript = postScript;
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@code 1}, higher priority than pure java {@link CommandHook}s and lower than
     *         {@link ShellScriptHook}
     */
    public @Override int getPriority() {
        return 1;
    }

    @Override
    public <C extends AbstractGeoGigOp<?>> C pre(C command) {
        if (preScript == null) {
            return command;
        }

        Scripting.runJVMScript(command, preScript);

        return command;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {

        if (postScript == null) {
            return (T) retVal;
        }
        boolean success = exception == null;

        if (success) {
            Scripting.runJVMScript(command, postScript);
        }
        return (T) retVal;
    }

    @Override
    public boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return true;
    }

}
