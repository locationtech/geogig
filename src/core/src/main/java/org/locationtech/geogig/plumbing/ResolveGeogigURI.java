/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;

/**
 * Resolves the location of the {@code .geogig} repository directory relative to the
 * {@link Platform#pwd() current directory}.
 * <p>
 * The location can be a either the current directory, a parent of it, or {@code null} if no
 * {@code .geogig} directory is found.
 * 
 */
public class ResolveGeogigURI extends AbstractGeoGigOp<Optional<URI>> {

    private @Nullable Platform platform;

    private @Nullable Hints hints;

    @Inject
    public ResolveGeogigURI(Platform platform, @Nullable Hints hints) {
        Preconditions.checkNotNull(platform);
        this.platform = platform;
        this.hints = hints;
    }

    public static Optional<URI> lookup(final File directory) {
        try {
            return Optional.fromNullable(lookupGeogigDirectory(directory));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Platform platform() {
        return this.platform == null ? super.platform() : this.platform;
    }

    /**
     * @return the location of the {@code .geogig} repository environment directory or {@code null}
     *         if not inside a working directory
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected Optional<URI> _call() {
        final Optional<URI> repoLocation;

        Optional<Serializable> repoUrl = Optional.absent();
        if (hints != null && (repoUrl = hints.get(Hints.REPOSITORY_URL)).isPresent()) {
            try {
                URI uri = new URI(String.valueOf(repoUrl.get()));
                if (Strings.isNullOrEmpty(uri.getScheme())) {
                    File f = new File(uri.toString());
                    if (!".geogig".equals(f.getName())) {
                        uri = new File(f, ".geogig").toURI();
                    }
                } else if ("file".equals(uri.getScheme())) {
                    File f = new File(uri);
                    if (!".geogig".equals(f.getName())) {
                        uri = new File(f, ".geogig").toURI();
                    }
                }
                repoLocation = Optional.of(uri);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(
                        "Repository URL is not a valid URI: " + repoUrl.get(), e);
            }
        } else {
            File pwd = platform().pwd();
            repoLocation = ResolveGeogigURI.lookup(pwd);
        }
        return repoLocation;
    }

    /**
     * @param file the directory to search
     * @return the location of the {@code .geogig} repository environment directory or {@code null}
     *         if not inside a working directory
     */
    private static URI lookupGeogigDirectory(@Nullable File file) throws IOException {
        if (file == null || !file.exists()) {
            return null;
        }
        if (file.isDirectory()) {
            if (file.getName().equals(".geogig")) {
                return file.toURI();
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
