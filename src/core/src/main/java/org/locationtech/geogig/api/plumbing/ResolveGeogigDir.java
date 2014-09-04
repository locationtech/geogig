/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Platform;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * Resolves the location of the {@code .geogig} repository directory relative to the
 * {@link Platform#pwd() current directory}.
 * <p>
 * The location can be a either the current directory, a parent of it, or {@code null} if no
 * {@code .geogig} directory is found.
 * 
 */
public class ResolveGeogigDir extends AbstractGeoGigOp<Optional<URL>> {

    private Platform platform;

    public ResolveGeogigDir() {
        //
    }

    public ResolveGeogigDir(Platform platform) {
        this.platform = platform;
    }

    public static Optional<URL> lookup(final File directory) {
        try {
            return Optional.fromNullable(lookupGeogigDirectory(directory));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected Platform platform() {
        return this.platform == null ? super.platform() : this.platform;
    }

    /**
     * @return the location of the {@code .geogig} repository environment directory or {@code null}
     *         if not inside a working directory
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected Optional<URL> _call() {
        File pwd = platform().pwd();
        Optional<URL> repoLocation = ResolveGeogigDir.lookup(pwd);
        return repoLocation;
    }

    public Optional<File> getFile() {
        Optional<URL> url = call();
        if (url.isPresent()) {
            try {
                if ("file".equalsIgnoreCase(url.get().getProtocol())) {
                    return Optional.of(new File(url.get().toURI()));
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return Optional.absent();
    }

    /**
     * @param file the directory to search
     * @return the location of the {@code .geogig} repository environment directory or {@code null}
     *         if not inside a working directory
     */
    private static URL lookupGeogigDirectory(@Nullable File file) throws IOException {
        if (file == null) {
            return null;
        }
        if (file.isDirectory()) {
            if (file.getName().equals(".geogig")) {
                return file.toURI().toURL();
            }
            File[] contents = file.listFiles();
            Preconditions.checkNotNull(contents,
                    "Either '%s' is not a directory or an I/O error ocurred listing its contents",
                    file.getAbsolutePath());
            for (File dir : contents) {
                if (dir.isDirectory() && dir.getName().equals(".geogig")) {
                    return lookupGeogigDirectory(dir);
                }
            }
        }
        return lookupGeogigDirectory(file.getParentFile());
    }

}
