/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

/**
 * A symbolic {@link Ref reference} is a {@code Ref} that links to a concrete {@code Ref}, just like
 * a symbolic ref in a Unix file system.
 * <p>
 * An example of a symbolic refs is the {@code HEAD} ref, that points to the current branch in
 * {@code refs/heads/<branchname>}.
 * <p>
 * Use the {@link #getTarget()} method to access the fully qualified name of the symbolic ref's
 * target, and {@link #getObjectId()} to get the identifier of the object (commit, tag, root tree)
 * the target ref points to at the time the {@code SymRef} was obtained from the repository. Beware
 * the target id may have changed since though, so depending on the context it may or may not be
 * needed to re-evaluate the value of the target ref.
 * 
 * @since 1.0
 */
public class SymRef extends Ref {

    private String target;

    /**
     * Constructs a new {@code SymRef} with the given name and target reference.
     * 
     * @param name the name of the symbolic reference
     * @param target the reference that this symbolic ref points to
     */
    public SymRef(String name, Ref target) {
        super(name, target.getObjectId());
        this.target = target.getName();
    }

    public SymRef(String name, String target, ObjectId targetId) {
        super(name, targetId);
        this.target = target;
    }

    /**
     * @return the reference that this symbolic ref points to
     */
    public String getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return String.format("%s -> [%s -> %s]", getName(), target, getObjectId());
    }

    /**
     * @return the non-symbolic {@link Ref} this symbolic reference points to.
     */
    public @Override Ref peel() {
        return new Ref(target, getObjectId());
    }

}
