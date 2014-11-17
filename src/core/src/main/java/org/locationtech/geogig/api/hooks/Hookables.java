/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.hooks;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.ServiceLoader;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Context;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A class for managing GeoGig operations that can be hooked and the filenames of the corresponding
 * hooks. It also includes additional related utilities.
 * 
 */
public class Hookables {

    private static final ImmutableList<CommandHook> classPathHooks;
    static {
        classPathHooks = Hookables.loadClasspathHooks();
    }

    /**
     * Returns the filename to be used for a script corresponding to the hook for a given GeoGig
     * operation. Returns {@link Optional.absent} if the specified operation does not allows hooks
     * 
     * @param class the operation
     * @return the string to be used as filename for storing the script files for the corresponding
     *         hook
     */
    public static Optional<String> getFilename(Class<? extends AbstractGeoGigOp<?>> clazz) {
        Hookable annotation = clazz.getAnnotation(Hookable.class);
        if (annotation != null) {
            return Optional.of(annotation.name());
        } else {
            return Optional.absent();
        }
    }

    public static ImmutableList<CommandHook> loadClasspathHooks() {
        ServiceLoader<CommandHook> loader = ServiceLoader.load(CommandHook.class);
        ImmutableList<CommandHook> SPIHooks = ImmutableList.copyOf(loader.iterator());
        return SPIHooks;
    }

    public static boolean hasClasspathHooks(Class<? extends AbstractGeoGigOp<?>> commandClass) {
        for (CommandHook hook : classPathHooks) {
            if (hook.appliesTo(commandClass)) {
                return true;
            }
        }
        return false;
    }

    public static List<CommandHook> findHooksFor(AbstractGeoGigOp<?> operation) {

        @SuppressWarnings("unchecked")
        final Class<? extends AbstractGeoGigOp<?>> clazz = (Class<? extends AbstractGeoGigOp<?>>) operation.getClass();

        List<CommandHook> hooks = Lists.newLinkedList();
        /*
         * First add any classpath hook, as they can be added to any command, regardless of having
         * the @Hookable annotation or not
         */
        for (CommandHook hook : classPathHooks) {
            if (hook.appliesTo(clazz)) {
                hooks.add(hook);
            }
        }

        /*
         * Now add any script hook that's configured for the operation iif it's @Hookable
         */
        final Optional<String> name = Hookables.getFilename(clazz);
        if (!name.isPresent()) {
            return hooks;
        }

        final File hooksDir = findHooksDirectory(operation);
        if (hooksDir == null) {
            return hooks;
        }

        if (name.isPresent()) {
            String preHookName = "pre_" + name.get().toLowerCase();
            String postHookName = "post_" + name.get().toLowerCase();
            File[] files = hooksDir.listFiles();
            for (File file : files) {
                String filename = file.getName();
                if (isHook(filename, preHookName)) {
                    hooks.add(Scripting.createScriptHook(file, true));
                }
                if (isHook(filename, postHookName)) {
                    hooks.add(Scripting.createScriptHook(file, false));
                }
            }
        }
        return hooks;

    }

    /**
     * Looks up for the hooks directory in the repository {@code operation} works on.
     * <p>
     * Implementation note: this method must not create any command through
     * {@link Context#command(Class)} either directly or indirectly or a stack overflow exception
     * would be thrown
     * 
     * @return {@code null} if the {@code operation} is not running on a repository, or the
     *         repository has no {@code hooks} directory at all.
     */
    @Nullable
    private static File findHooksDirectory(AbstractGeoGigOp<?> operation) {
        if (operation.context().repository() == null
                || operation.context().repository().getLocation() == null) {
            return null;
        }
        URL url = operation.context().repository().getLocation();
        if (!"file".equals(url.getProtocol())) {
            // Hooks not in a filesystem are not supported
            return null;
        }
        File repoDir;
        try {
            repoDir = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
        File hooksDir = new File(repoDir, "hooks");
        if (!hooksDir.exists()) {
            return null;
        }
        return hooksDir;
    }

    private static boolean isHook(final String filename, final String hookNamePrefix) {
        if (hookNamePrefix.equals(filename) || filename.startsWith(hookNamePrefix + ".")) {
            if (!filename.endsWith("sample")) {
                return true;
            }
        }
        return false;
    }

}
