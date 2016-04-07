/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.hooks.Hookables;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;

public class DefaultRepositoryResolver extends RepositoryResolver {

    @Override
    public boolean canHandle(URI repoURI) {
        String scheme = repoURI.getScheme();
        if (null == scheme) {
            File file = toFile(repoURI);
            boolean exists = file.exists();
            boolean directory = file.isDirectory();
            boolean parentExists = file.getParentFile().exists();
            return (exists && directory) || parentExists;
        }
        return "file".equals(scheme);
    }

    private File toFile(URI repoURI) {
        String scheme = repoURI.getScheme();
        File file;
        if (scheme == null) {
            file = new File(repoURI.toString());
        } else {
            file = new File(repoURI);
        }
        return file;
    }

    @Override
    public boolean repoExists(URI repoURI) {
        File directory = toFile(repoURI);
        Optional<URI> lookup = ResolveGeogigURI.lookup(directory);
        return lookup.isPresent();
    }

    @Override
    public String getName(URI repoURI) {
        File file = toFile(repoURI);
        try {
            file = file.getCanonicalFile();
            if (file.getName().equals(".geogig")) {
                file = file.getParentFile();
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return file.getName();
    }

    @Override
    public void initialize(URI repoURI, Context repoContext) throws IllegalArgumentException {

        final boolean repoExisted = repoExists(repoURI);

        final File targetDir = toFile(repoURI);

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalArgumentException("Can't create directory "
                    + targetDir.getAbsolutePath());
        }

        Platform platform = repoContext.platform();
        platform.setWorkingDir(targetDir);

        final File envHome;
        if (repoExisted) {
            // we're at either the repo working dir or a subdirectory of it
            envHome = targetDir;
        } else {
            if (targetDir.getName().equals(".geogig")) {
                envHome = targetDir;
            } else {
                envHome = new File(targetDir, ".geogig");
                if (!envHome.mkdirs()) {
                    throw new RuntimeException("Unable to create .geogig directory at '"
                            + envHome.getAbsolutePath() + "'");
                }
            }
        }
        createSampleHooks(envHome);
    }

    @Override
    public ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext) {
        return new IniFileConfigDatabase(repoContext.platform());
    }

    private void createSampleHooks(File envHome) {
        File hooks = new File(envHome, "hooks");
        hooks.mkdirs();
        if (!hooks.exists()) {
            throw new RuntimeException();
        }
        try {
            copyHookFile(hooks.getAbsolutePath(), "pre_commit.js.sample");
            // TODO: add other example hooks
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private void copyHookFile(String folder, String file) throws IOException {
        URL url = Resources.getResource(Hookables.class, file);
        OutputStream os = new FileOutputStream(new File(folder, file).getAbsolutePath());
        Resources.copy(url, os);
        os.close();
    }

    @Override
    public Repository open(URI repositoryLocation) throws RepositoryConnectionException {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a file repository: %s",
                repositoryLocation);

        if (!repoExists(repositoryLocation)) {
            throw new RepositoryConnectionException(repositoryLocation
                    + " is not a geogig repository");
        }

        File workingDir = toFile(repositoryLocation);
        GeoGIG geoGIG = new GeoGIG(workingDir);
        Repository repository = geoGIG.getRepository();
        repository.open();

        return repository;
    }

    @Override
    public boolean delete(URI repositoryLocation) throws Exception {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a file repository: %s",
                repositoryLocation);

        if (!repoExists(repositoryLocation)) {
            return false;
        }

        File workingDir = toFile(repositoryLocation);
        deleteDirectoryAndContents(workingDir);

        return true;
    }

    private void deleteDirectoryAndContents(File directory) throws IOException {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectoryAndContents(file);
            } else {
                if (!file.delete()) {
                    throw new IOException("Unable to delete file: " + file.getCanonicalPath());
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Unable to delete directory: " + directory.getCanonicalPath());
        }
    }
}
