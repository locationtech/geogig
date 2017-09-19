/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;

import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * {@link RemoteResolver} implementation that works against the HTTP web API
 */
public class HttpRemoteResolver implements RemoteResolver {

    public @Override Optional<IRemoteRepo> resolve(Remote remote, Hints remoteHints) {

        IRemoteRepo remoteRepo = null;

        try {
            String fetchURL = remote.getFetchURL();
            URI fetchURI = URI.create(fetchURL);
            final String protocol = fetchURI.getScheme();

            if ("http".equals(protocol) || "https".equals(protocol)) {
                final String username = remote.getUserName();
                final String password = remote.getPassword();
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
                if (remote.getMapped()) {
                    remoteRepo = new HttpMappedRemoteRepo(remote, fetchURI.toURL());
                } else {
                    remoteRepo = new HttpRemoteRepo(remote, fetchURI.toURL());
                }

            }
        } catch (Exception e) {
            // Invalid fetch URL
            Throwables.propagate(e);
        }
        return Optional.fromNullable(remoteRepo);
    }
}
