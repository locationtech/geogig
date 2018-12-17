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
import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import lombok.NonNull;

public class ScriptHookWrapper implements CommandHook {

    public @Override boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return clazz.isAnnotationPresent(Hookable.class);
    }

    public @Override @NonNull List<CommandHook> unwrap(@NonNull AbstractGeoGigOp<?> command) {
        return findScriptHooksFor(command);
    }

    public @Override <C extends AbstractGeoGigOp<?>> C pre(C command) {
        throw new UnsupportedOperationException();
    }

    public @Override <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {

        throw new UnsupportedOperationException();
    }

    /**
     * Returns the filename to be used for a script corresponding to the hook for a given GeoGig
     * operation. Returns {@link Optional.absent} if the specified operation does not allows hooks
     * 
     * @param class the operation
     * @return the string to be used as filename for storing the script files for the corresponding
     *         hook
     */
    static Optional<String> getFilename(Class<? extends AbstractGeoGigOp<?>> clazz) {
        Hookable annotation = clazz.getAnnotation(Hookable.class);
        if (annotation != null) {
            return Optional.of(annotation.name());
        } else {
            return Optional.absent();
        }
    }

    public static List<CommandHook> findScriptHooksFor(AbstractGeoGigOp<?> operation) {

        @SuppressWarnings("unchecked")
        final Class<? extends AbstractGeoGigOp<?>> clazz = (Class<? extends AbstractGeoGigOp<?>>) operation
                .getClass();

        List<CommandHook> hooks = Lists.newLinkedList();

        /*
         * add any script hook that's configured for the operation iif it's @Hookable
         */
        final Optional<String> name = getFilename(clazz);
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
        URI url = operation.context().repository().getLocation();
        if (!"file".equals(url.getScheme())) {
            // Hooks not in a filesystem are not supported
            return null;
        }
        File repoDir = new File(url);
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
