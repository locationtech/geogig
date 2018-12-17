/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.scripting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Repository;

import com.google.common.io.Resources;

/**
 * Post-hook on {@link InitOp} that creates a sample {@code hooks} directory and sample script hooks
 * under the repository folder in case the initialized repository is file based.
 */
public class InitializeHooksHook implements CommandHook {

    public @Override boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return InitOp.class.isAssignableFrom(clazz);
    }

    public @Override <C extends AbstractGeoGigOp<?>> C pre(C command) {
        return command;
    }

    @SuppressWarnings("unchecked")
    public @Override <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {

        if (exception == null) {
            Repository repo = (Repository) retVal;
            URI location = repo.getLocation();
            if ("file".equals(location.getScheme())) {
                File envHome = new File(location);
                createSampleHooks(envHome);
            }
        }
        return (T) retVal;
    }

    private void createSampleHooks(File envHome) {
        File hooks = new File(envHome, "hooks");
        hooks.mkdirs();
        if (!hooks.exists()) {
            throw new RuntimeException();
        }
        try {
            copyHookFile(hooks.getAbsolutePath(), "pre_commit.js.sample");
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private void copyHookFile(String folder, String file) throws IOException {
        URL url = Resources.getResource(GeoGigAPI.class, file);
        OutputStream os = new FileOutputStream(new File(folder, file).getAbsolutePath());
        Resources.copy(url, os);
        os.close();
    }
}
