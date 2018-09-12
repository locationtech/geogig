/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A named pointer to a {@link RevObject} that represents an entry point in a repository's revision
 * graph.
 * <p>
 * The commit history consists of a DAG of commits, each one pointing to it's parent(s) commit(s).
 * {@code Ref}s are essentially named pointers to commits, where the ref name is usually the name of
 * a branch (when the ref is in the {@code refs/heads/<refName>} namespace), or the name of a branch
 * in a remote repository (when the ref is in the {@code refs/remotes/<remoteName>/<refName>}
 * namespace).
 * <p>
 * Types of {@link Ref}s
 * <ul>
 * <li>Branches: Refs under the {@code refs/heads} namespace, point to {@link RevCommit commits}
 * <li>Remote Branches: Refs under the {@code refs/remotes/<remoteName>} namespace, point to
 * {@link RevCommit commits}
 * <li>Tags: Refs under the {@code refs/tags} namespace, point to {@link RevTag tags}
 * <li>Transaction refs: refs under the {@code transactions/<transaction id>} namespace form a whole
 * lot of refs like the ones in the root repository (e.g. {@code transactions/<txId>/HEAD},
 * {@code transactions/<txId>/refs/heads/master}, etc. They are created when the transaction
 * namespace is created as a copy of the current state of all the refs in the repository, and when
 * the transaction is committed, the refs that have changed in the transaction namespace override
 * the ones in the main repository ref set.
 * <li>{@link #WORK_HEAD} is a ref that points to the root {@link RevTree} that represents the
 * current working tree
 * <li>{@link #STAGE_HEAD} is a ref that points to the root {@link RevTree} that represents the
 * current staging area
 * </ul>
 * 
 * @see SymRef
 * 
 * @since 1.0
 */
public class Ref implements Comparable<Ref> {

    /**
     * By convention, name of the main branch
     */
    public static final String MASTER = "refs/heads/master";

    /**
     * Pointer to the latest commit in the current branch
     */
    public static final String HEAD = "HEAD";

    /**
     * Pointer to the current tree in the staging index
     */
    public static final String STAGE_HEAD = "STAGE_HEAD";

    /**
     * Pointer to the current tree in the working directory
     */
    public static final String WORK_HEAD = "WORK_HEAD";

    /**
     * Pointer to the commit to be merged during a merge operation
     */
    public static final String MERGE_HEAD = "MERGE_HEAD";

    /**
     * Pointer to the commit to be picked during a cherry-pick operation
     */
    public static final String CHERRY_PICK_HEAD = "CHERRY_PICK_HEAD";

    /**
     * Pointer to the commit onto which another commit is to be merged during a merge operation
     */
    public static final String ORIG_HEAD = "ORIG_HEAD";

    /**
     * Directory prefix for refs.
     */
    public static final String REFS_PREFIX = "refs/";

    /**
     * Directory prefix for remotes.
     */
    public static final String REMOTES_PREFIX = REFS_PREFIX + "remotes/";

    /**
     * Directory prefix for tags.
     */
    public static final String TAGS_PREFIX = REFS_PREFIX + "tags/";

    /**
     * Directory prefix for heads.
     */
    public static final String HEADS_PREFIX = REFS_PREFIX + "heads/";

    /**
     * Directory prefix for transaction refs (i.e. where all refs are copied for a specific
     * transaction under {@code TRANSACTIONS_PREFIX + "/<transaction id>/}
     */
    public static final String TRANSACTIONS_PREFIX = REFS_PREFIX + "transactions/";

    /**
     * By convention, the origin of the repository
     */
    public static final String ORIGIN = "refs/remotes/origin";

    private String name;

    private ObjectId objectId;

    /**
     * Constructs a new Ref with the given parameters.
     * 
     * @param name name of this ref
     * @param oid object id for this ref
     */
    public Ref(final String name, final ObjectId oid) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(oid);
        this.name = name;
        this.objectId = oid;
    }

    /**
     * @return the name for this ref
     */
    public String getName() {
        return name;
    }

    /**
     * @return the name for this ref with the prefix removed
     */
    public String localName() {
        return localName(name);
    }

    /**
     * @param ref the ref string
     * @return the local name of the given ref string
     */
    public static String localName(final String ref) {

        if (ref.startsWith(HEADS_PREFIX)) {
            return ref.substring(HEADS_PREFIX.length());
        } else if (ref.startsWith(TAGS_PREFIX)) {
            return ref.substring(TAGS_PREFIX.length());
        } else if (ref.startsWith(REMOTES_PREFIX)) {
            String remoteWithBranch = ref.substring(REMOTES_PREFIX.length());
            return remoteWithBranch.substring(remoteWithBranch.indexOf('/') + 1);
        } else if (ref.startsWith(REFS_PREFIX)) {
            return ref.substring(REFS_PREFIX.length());
        }
        return ref;
    }

    /**
     * @return the namespace for this ref, ends with a /
     */
    public String namespace() {
        return namespace(name);
    }

    /**
     * @param ref the ref string
     * @return the namespace of the given ref string
     */
    public static String namespace(final String ref) {
        if (ref.startsWith(HEADS_PREFIX)) {
            return HEADS_PREFIX;
        } else if (ref.startsWith(TAGS_PREFIX)) {
            return TAGS_PREFIX;
        } else if (ref.startsWith(REMOTES_PREFIX)) {
            String remote = ref.substring(REMOTES_PREFIX.length());
            remote = remote.substring(0, remote.indexOf('/'));
            return REMOTES_PREFIX + remote + "/";
        } else if (ref.startsWith(REFS_PREFIX)) {
            return REFS_PREFIX;
        }
        return ref;
    }

    public static Optional<String> remoteName(final String ref) {
        if (ref.startsWith(REMOTES_PREFIX)) {
            String remote = ref.substring(REMOTES_PREFIX.length());
            remote = remote.substring(0, remote.indexOf('/'));
            return Optional.of(remote);
        }
        return Optional.empty();
    }

    /**
     * @return the parent path of {@code ref}, or the empty string if ref is a root ref like HEAD,
     *         STAGE_HEAD, etc.
     */
    public static String parentPath(final String ref) {
        int idx = ref.lastIndexOf('/');
        if (idx == -1) {
            return "";
        }
        return ref.substring(0, idx);
    }

    /**
     * @return the ref's name without any path information (e.g. "master" from "refs/heads/master").
     */
    public static String simpleName(final String ref) {
        int idx = ref.lastIndexOf('/');
        if (idx == -1) {
            return ref;
        }
        return ref.substring(idx + 1);
    }

    /**
     * @return the object id being referenced
     */
    public ObjectId getObjectId() {
        return objectId;
    }

    /**
     * @param o object to compare against
     * @return whether or not this ref is equal to the target object
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Ref)) {
            return false;
        }
        Ref r = (Ref) o;
        return name.equals(r.getName()) && objectId.equals(r.getObjectId());
    }

    /**
     * @return a hash code for this ref
     */
    @Override
    public int hashCode() {
        return name.hashCode() * objectId.hashCode();
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Ref o) {
        return name.compareTo(o.getName());
    }

    /**
     * @return the ref represented by a readable string
     */
    @Override
    public String toString() {
        return String.format("[%s -> %s]", name, objectId);
    }

    public static String append(String namespace, String child) {
        StringBuilder sb = new StringBuilder();
        if (namespace.endsWith("/")) {
            namespace = namespace.substring(0, namespace.length() - 1);
        }
        sb.append(namespace);

        if (child.length() > 0 && child.charAt(0) == '/') {
            child = child.substring(1);
        }
        if (child.endsWith("/")) {
            child = child.substring(0, child.length() - 1);
        }
        if (!child.isEmpty()) {
            if (!namespace.isEmpty()) {
                sb.append('/');
            }
            sb.append(child);
        }
        return sb.toString();
    }

    /**
     * @return the relative name of the ref given by its full name and the namespace to truncate
     */
    public static String child(String namespace, String ref) {
        Preconditions.checkState(ref.startsWith(namespace));
        String relative = ref.substring(namespace.length());
        if (relative.length() > 0 && relative.charAt(0) == '/') {
            relative = relative.substring(1);
        }
        return relative;
    }

    /**
     * Returns the resolved ref this ref points to.
     * <p>
     * For regular refs, just returns {@code this}, {@link SymRef symbolic refs} return the ref they
     * point to.
     */
    public Ref peel() {
        return this;
    }

    /**
     * Determines if the {@code ref} is a child of {@code parent}
     * 
     * @param parent the parent ref name
     * @param nodePath the path of the node
     * @return true if {@code nodePath} is a child of {@code parentPath} at any depth level,
     *         {@code false} if unrelated, sibling, or same path
     */
    public static boolean isChild(String parent, String ref) {
        checkNotNull(parent, "parent");
        checkNotNull(ref, "ref");

        int parentSeparatorIndex = parent.endsWith("/") ? parent.length() - 1 : parent.length();

        return ref.length() > parent.length()
                && (parent.isEmpty() || ref.charAt(parentSeparatorIndex) == '/')
                && ref.startsWith(parent);
    }

    public static String stripCommonPrefix(final String ref) {
        Preconditions.checkNotNull(ref);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(ref));
        if (ref.indexOf('/') == -1) {
            return ref;
        }
        if (ref.startsWith(Ref.HEADS_PREFIX)) {
            return ref.substring(Ref.HEADS_PREFIX.length());
        }
        if (ref.startsWith(Ref.TAGS_PREFIX)) {
            return ref.substring(Ref.TAGS_PREFIX.length());
        }
        if (ref.startsWith(Ref.REFS_PREFIX)) {
            return ref.substring(Ref.REFS_PREFIX.length());
        }
        return ref;
    }

}