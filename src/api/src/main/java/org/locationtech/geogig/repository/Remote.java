/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.Ref;

import org.locationtech.geogig.base.Strings;

import lombok.NonNull;

/**
 * Internal representation of a GeoGig remote repository.
 * 
 * @since 1.0
 */
public class Remote {
    private String name;

    private String fetchurl;

    private String pushurl;

    private List<LocalRemoteRefSpec> fetchSpecs;

    private String fetch;

    private @NonNull String mappedBranch;

    private String username;

    private String password;

    private boolean mapped;

    /**
     * Constructs a new remote with the given parameters.
     * 
     * @param name the name of the remote
     * @param fetchurl the fetch URL of the remote
     * @param pushurl the push URL of the remote
     * @param fetch the fetch string of the remote
     * @param mapped whether or not this remote is mapped
     * @param mappedBranch the branch the remote is mapped to
     * @param username the user name to access the repository
     * @param password the password to access the repository
     */
    public Remote(@NonNull String name, String fetchurl, String pushurl, String fetch,
            boolean mapped, String mappedBranch, String username, String password) {
        this.name = name;
        this.fetchurl = checkURL(fetchurl);
        this.pushurl = checkURL(pushurl);
        this.fetch = fetch;
        this.mapped = mapped;
        this.mappedBranch = Optional.ofNullable(mappedBranch).orElse("*");
        this.username = username;
        this.password = password;
        this.fetchSpecs = Strings.isNullOrEmpty(fetch) ? Collections.emptyList()
                : Collections.unmodifiableList(LocalRemoteRefSpec.parse(name, fetch));
    }

    /**
     * Ensure that the provided url is valid and not {@code null}.
     * 
     * @param url the url to check
     * @return the url, if it passed validation
     * @throws IllegalArgumentException
     */
    private String checkURL(String url) {
        Preconditions.checkArgument(url != null, "Invalid remote URL: %s", url);

        try {
            new URI(url);
        } catch (URISyntaxException e) {
            // See if it's a valid file URI
            try {
                new URI("file:/" + url);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Invalid remote URL.");
            }
        }
        return url;
    }

    /**
     * @return the name of the remote
     */
    public String getName() {
        return name;
    }

    /**
     * @return the fetch URL of the remote
     */
    public String getFetchURL() {
        return fetchurl;
    }

    /**
     * @return the push URL of the remote
     */
    public String getPushURL() {
        return pushurl;
    }

    public String getFetchSpec() {
        return fetch;
    }

    public List<LocalRemoteRefSpec> getFetchSpecs() {
        return fetchSpecs;
    }

    /**
     * Returns the local ref a remote ref maps to according to the remote's remote-to-local refspec
     * rules.
     * 
     * @param remoteRef a ref in the remote's local namespace (e.g. {@code refs/heads/master}, not
     *        {@code refs/remotes/<remote>/master}
     * @return the ref name that maps the remote ref to the local repository ref according to the
     *         remote's {@link #getFetchSpecs() fetch-specs}, or
     *         {@code java.util.Optional#empty() empty()} if none matches.
     */
    public java.util.Optional<String> mapToLocal(@NonNull String remoteRef) {
        if (fetchSpecs.isEmpty()) {
            if (remoteRef.startsWith(Ref.TAGS_PREFIX)) {
                return java.util.Optional.of(remoteRef);
            }
            String remoteSimpleName = remoteRef.startsWith(Ref.HEADS_PREFIX)
                    ? remoteRef.substring(Ref.REMOTES_PREFIX.length())
                    : remoteRef;
            String localRef = String.format("refs/remotes/%s/%s", this.name, remoteSimpleName);
            return java.util.Optional.of(localRef);
        }

        for (LocalRemoteRefSpec spec : fetchSpecs) {
            java.util.Optional<String> localRef = spec.mapToLocal(remoteRef);
            if (localRef.isPresent()) {
                return localRef;
            }
        }

        return java.util.Optional.empty();
    }

    public java.util.Optional<String> mapToRemote(final @NonNull String localRef) {
        if (fetchSpecs.isEmpty()) {
            if (localRef.startsWith(Ref.TAGS_PREFIX)) {
                return java.util.Optional.of(localRef);
            }
            return java.util.Optional.of(localRef);// match local to remote
        }

        for (LocalRemoteRefSpec spec : fetchSpecs) {
            java.util.Optional<String> remoteRef = spec.mapToRemote(localRef);
            if (remoteRef.isPresent()) {
                return remoteRef;
            }
        }

        return java.util.Optional.empty();
    }

    /**
     * @return whether or not this remote is mapped
     */
    public boolean isMapped() {
        return mapped;
    }

    /**
     * @return the branch the remote is mapped to, or {@literal *} indicating all branches
     */
    public @NonNull String getMappedBranch() {
        return mappedBranch;
    }

    /**
     * @return the user name to access the repository
     */
    public String getUserName() {
        return username;
    }

    /**
     * @return the password to access the repository
     */
    public String getPassword() {
        return password;
    }

    public @Override String toString() {
        return String.format("%s [%s]", getName(), fetch);
    }

    /**
     * Determines if this Remote is the same as the given Remote.
     * 
     * @param o the remote to compare against
     */
    public @Override boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Remote)) {
            return false;
        }
        Remote r = (Remote) o;
        return fetch.equals(r.fetch) && fetchurl.equals(r.fetchurl) && pushurl.equals(r.pushurl)
                && name.equals(r.name) && (mapped == r.mapped)
                && stringsEqual(mappedBranch, r.mappedBranch) && stringsEqual(username, r.username)
                && stringsEqual(password, r.password);
    }

    private boolean stringsEqual(String s1, String s2) {
        return (s1 == null ? s2 == null : s1.equals(s2));
    }

    public Remote fetch(@NonNull String localRemoteRefSpec) {
        Remote branchRemote = new Remote(name, fetchurl, pushurl, localRemoteRefSpec, mapped,
                mappedBranch, username, password);
        return branchRemote;
    }

    public static String defaultRemoteRefSpec(String remoteName) {
        return String.format("+refs/heads/*:refs/remotes/%s/*;+refs/tags/*:refs/tags/*",
                remoteName);
    }

    public static String defaultMappedBranchRefSpec(String remoteName, String branch) {
        return String.format("+refs/heads/%s:refs/remotes/%s/%s", branch, remoteName, branch);
    }
}
