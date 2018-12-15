/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing.remotes;

import java.util.List;

import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.remotes.RemoteException.StatusCode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.ConfigDatabase;

/**
 * Adds a remote to the local config database.
 * 
 * @see ConfigDatabase
 * @see Remote
 */
public class RemoteAddOp extends AbstractGeoGigOp<Remote> {

    private String name;

    private String url;

    private String branch;

    private String username;

    private String password;

    private boolean mapped = false;

    /**
     * Executes the remote-add operation.
     * 
     * @return the {@link Remote} that was added.
     */
    @Override
    protected Remote _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }
        if (url == null || url.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_URL);
        }
        if (branch == null || branch.isEmpty()) {
            branch = "*";
        }
        command(CheckRefFormat.class).setThrowsException(true).setAllowOneLevel(true).setRef(name)
                .call();

        ConfigDatabase config = configDatabase();
        List<String> allRemotes = config.getAllSubsections("remote");
        if (allRemotes.contains(name)) {
            throw new RemoteException(StatusCode.REMOTE_ALREADY_EXISTS);
        }

        String configSection = "remote." + name;
        final String fetch = "*".equals(branch) ? Remote.defaultRemoteRefSpec(name)
                : Remote.defaultMappedBranchRefSpec(name, branch);

        config.put(configSection + ".url", url);
        config.put(configSection + ".fetch", fetch);
        if (mapped) {
            config.put(configSection + ".mapped", "true");
            config.put(configSection + ".mappedBranch", branch);
        }
        if (username != null) {
            config.put(configSection + ".username", username);
        }

        return new Remote(name, url, url, fetch, mapped, branch, username, password);
    }

    /**
     * @param name the name of the remote
     * @return {@code this}
     */
    public RemoteAddOp setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    /**
     * @param url the URL of the remote
     * @return {@code this}
     */
    public RemoteAddOp setURL(String url) {
        this.url = url;
        return this;
    }

    public String getURL() {
        return url;
    }

    /**
     * @param branch a specific branch to track
     * @return {@code this}
     */
    public RemoteAddOp setBranch(String branch) {
        this.branch = branch;
        return this;
    }

    public String getBranch() {
        return branch;
    }

    /**
     * @param username user name for the repository
     * @return {@code this}
     */
    public RemoteAddOp setUserName(String username) {
        this.username = username;
        return this;
    }

    public String getUserName() {
        return username;
    }

    /**
     * @param password password for the repository
     * @return {@code this}
     */
    public RemoteAddOp setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @param mapped whether or not this is a mapped remote
     * @return {@code this}
     */
    public RemoteAddOp setMapped(boolean mapped) {
        this.mapped = mapped;
        return this;
    }

    public boolean isMapped() {
        return mapped;
    }
}
