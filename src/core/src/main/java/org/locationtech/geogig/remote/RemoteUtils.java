/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remote;

import java.io.File;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.CreateDeduplicator;
import org.locationtech.geogig.remote.http.HttpMappedRemoteRepo;
import org.locationtech.geogig.remote.http.HttpRemoteRepo;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.DeduplicationService;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Provides utilities for creating interfaces to remote repositories.
 */
public class RemoteUtils {

    /**
     * Constructs an interface to allow access to a remote repository.
     * 
     * @param localRepository the local repository
     * @param remoteConfig the remote to connect to
     * @param remoteHints hints for the remote repo, like read-only, etc.
     * @return an {@link Optional} of the interface to the remote repository, or
     *         {@link Optional#absent()} if a connection to the remote could not be established.
     */
    public static Optional<IRemoteRepo> newRemote(Repository localRepository, Remote remoteConfig,
            @Nullable Hints remoteHints) {

        if (remoteHints == null) {
            remoteHints = new Hints();
        }
        try {
            String fetchURL = remoteConfig.getFetchURL();
            URI fetchURI = URI.create(fetchURL);
            if (null == fetchURI.getScheme()) {
                fetchURI = new File(fetchURL).toURI();
            }
            final String protocol = fetchURI.getScheme();

            IRemoteRepo remoteRepo = null;
            if (protocol.equals("http") || protocol.equals("https")) {
                final String username = remoteConfig.getUserName();
                final String password = remoteConfig.getPassword();
                if (username != null && password != null) {
                    Authenticator.setDefault(new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username,
                                    Remote.decryptPassword(password).toCharArray());
                        }
                    });
                } else {
                    Authenticator.setDefault(null);
                }
                if (remoteConfig.getMapped()) {
                    remoteRepo = new HttpMappedRemoteRepo(fetchURI.toURL(), localRepository);
                } else {
                    DeduplicationService deduplicationService;
                    deduplicationService = localRepository.command(CreateDeduplicator.class).call();
                    remoteRepo = new HttpRemoteRepo(fetchURI.toURL(), localRepository,
                            deduplicationService);
                }

            } else {
                if (remoteConfig.getMapped()) {
                    remoteRepo = new LocalMappedRemoteRepo(fetchURI, localRepository);
                } else {
                    remoteRepo = new LocalRemoteRepo(fetchURI, localRepository);
                }
            }

            return Optional.fromNullable(remoteRepo);
        } catch (Exception e) {
            // Invalid fetch URL
            Throwables.propagate(e);
        }

        return Optional.absent();
    }
}
