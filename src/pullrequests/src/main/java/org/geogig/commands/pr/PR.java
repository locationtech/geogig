/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.collect.ImmutableMap;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * 
 * Pull Request refs:
 * <ul>
 * <li>{@code refs/heads/<target branch>}: target branch state inside the pr transaction. Reset hard
 * to the live value when {@link PRPrepareOp} is called.
 * <li>{@link #getOriginRef() refs/pull/&lt;id&gt;/origin}: points to the commit the remote branch
 * pointed to when the PR was created or last fetched. Useful to figure out if the PR is outdated
 * wrt the current issuer repo branch.
 * <li>{@link #getHeadRef() refs/pull/&lt;id&gt;/head}: The pr working head, where the
 * {@code origin} and {@code target} refs merge is attempted, and possible conflicts belong while
 * the transaction is open. Initial value id {@link ObjectId#NULL NULL}, until {@link PRPrepareOp}
 * is called, which pulls the remote branch and initializes this ref.
 * <li>{@link #getMergeRef() refs/pull/&lt;id&gt;/merge}: absent unless a test-merge has been
 * performed, in which case points to the merge commit between the {@code target} and {@code origin}
 * refs.
 * </ul>
 *
 */
public @Data @Builder class PR {
    //@formatter:off
    static final String KEY_TRANSACTION= "transaction";
    static final String KEY_REMOTE = "remote";
    static final String KEY_REMOTEBRANCH = "remoteBranch";
    static final String KEY_TARGETBRANCH= "targetBranch";
    static final String KEY_TITLE = "title";
    static final String KEY_DESCRIPTION = "description";
    //@formatter:on

    private @NonNull Integer id;

    private @NonNull UUID transactionId;

    private @NonNull URI remote;

    private @NonNull String remoteBranch;

    private @NonNull String targetBranch;

    private @NonNull String title;

    private String description;

    public String getOriginRef() {
        return String.format("refs/pull/%d/origin", id);
    }

    public String getHeadRef() {
        return String.format("refs/pull/%d/head", id);
    }

    public String getMergeRef() {
        return String.format("refs/pull/%d/merge", id);
    }

    static PR createFromProperties(int prId, Map<String, String> props) {
        PR pr = PR.builder()//
                .id(prId)//
                .transactionId(UUID.fromString(props.get(KEY_TRANSACTION)))//
                .remote(URI.create(props.get(KEY_REMOTE)))//
                .remoteBranch(props.get(KEY_REMOTEBRANCH))//
                .targetBranch(props.get(KEY_TARGETBRANCH))//
                .title(props.get(KEY_TITLE))//
                .description(props.get(KEY_DESCRIPTION))//
                .build();
        return pr;
    }

    Map<String, String> toProperties() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        builder.put(KEY_TRANSACTION, transactionId.toString())//
                .put(KEY_REMOTE, remote.toString())//
                .put(KEY_REMOTEBRANCH, remoteBranch)//
                .put(KEY_TARGETBRANCH, targetBranch)//
                .put(KEY_TITLE, title);
        if (description != null) {
            builder.put(KEY_DESCRIPTION, description);
        }

        final Map<String, String> props = builder.build();
        return props;
    }

    Remote buildRemote() {
        final String prRef = getOriginRef();
        final String localRemoteRefSpec = String.format("+%s:%s", remoteBranch, prRef);

        String name = "pr" + id;
        String fetchurl = remote.toString();
        String pushurl = fetchurl;
        String fetch = localRemoteRefSpec;
        boolean mapped = false;
        String mappedBranch = null;
        String username = null;
        String password = null;
        Remote remote = new Remote(name, fetchurl, pushurl, fetch, mapped, mappedBranch, username,
                password);
        return remote;
    }

    GeogigTransaction getTransaction(Context repo) {
        GeogigTransaction tx = tryGetTransaction(repo)
                .orElseThrow(() -> new NoSuchElementException(String.format(
                        "Transaction %s of pull request %d does not exist", transactionId, id)));
        return tx;
    }

    Optional<GeogigTransaction> tryGetTransaction(Context repo) {
        Optional<GeogigTransaction> tx = com.google.common.base.Optional
                .toJavaUtil(repo.command(TransactionResolve.class).setId(transactionId).call());
        return tx;
    }

    IRemoteRepo openRemote(Context localRepo) {
        return localRepo.command(OpenRemote.class).setRemote(buildRemote()).call();
    }

    Ref resolveHeadRef(Context context) {
        return resolveRef(context, getHeadRef());
    }

    Ref resolveTargetBranch(Context prContext) {
        return resolveRef(prContext, targetBranch);
    }

    Ref resolveOriginRef(Context prContext) {
        return resolveRef(prContext, getOriginRef());
    }

    Optional<Ref> resolveMergeRef(Context prContext) {
        return resolveRefOpt(prContext, getMergeRef());
    }

    Optional<Ref> resolveRefOpt(Context prContext, String refName) {
        Ref ref = prContext.command(RefParse.class).setName(refName).call().orNull();
        return Optional.ofNullable(ref);
    }

    Ref resolveRef(Context prContext, String refName) {
        Ref ref = resolveRefOpt(prContext, refName).orElseThrow(
                () -> new NoSuchElementException(String.format("Ref %s does not exist", refName)));
        return ref;
    }

    Ref resolveRemoteBranch(Repository remoteRepo) {
        Ref issuerBranch = resolveRef(remoteRepo.context(), remoteBranch);
        return issuerBranch;
    }

    Repository openRemote() {
        Repository repository;
        try {
            repository = RepositoryResolver.load(remote);
        } catch (RepositoryConnectionException e) {
            throw new IllegalStateException("Unable to open pull request issuer repository", e);
        }
        return repository;
    }
}
