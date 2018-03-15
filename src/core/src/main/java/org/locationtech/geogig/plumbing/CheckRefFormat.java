/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.regex.Pattern;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Preconditions;

/**
 * Validates the format of a ref.
 * <p>
 * Each ref must include at least one slash (/) unless explicitly allowed via the
 * {@code allowOneLevel} parameter.
 * <p>
 * Rules for each slash-separated component of a ref are as follows:
 * <ul>
 * <li>They cannot be empty.
 * <li>They cannot begin or end with a dot (.).
 * <li>They cannot end with .lock.
 * <li>They cannot have ASCII control characters or any of the following: space, ~, ^, :, ?, *, [.
 * <li>They cannot have two consecutive dots (..) anywhere.
 * <li>They cannot contain a sequence (@{) anywhere.
 * <li>They cannot be the single character (@).
 * <li>They cannot contain a backslash (\) anywhere.
 * <li>They cannot have ASCII control characters (i.e. bytes whose values are lower than \040, or
 * \177 DEL), space, tilde ~, caret ^, or colon : anywhere.
 * </ul>
 */
public class CheckRefFormat extends AbstractGeoGigOp<Boolean> {

    private String ref = null;

    private boolean allowOneLevel = true;

    private boolean throwsException = false;

    /**
     * @param ref the ref to validate
     */
    public CheckRefFormat setRef(String ref) {
        this.ref = ref;
        return this;
    }

    /**
     * By default, CheckRefFormat will enforce the presence of a category specifier, such as
     * {@code refs/heads/master}. Specifying allowOneLevel will validate refs that do not have a
     * category, such as {@code master}.
     * 
     * @param allow if true, the operation will allow single level refs to be validated.
     */
    public CheckRefFormat setAllowOneLevel(boolean allow) {
        this.allowOneLevel = allow;
        return this;
    }

    /**
     * @param throwsException if true, the operation will throw an {@link IllegalArgumentException}
     *        when a rule is violated.
     */
    public CheckRefFormat setThrowsException(boolean throwsException) {
        this.throwsException = throwsException;
        return this;
    }

    @Override
    protected Boolean _call() {
        try {
            Preconditions.checkArgument(ref != null, "Ref was not provided.");

            String[] tokens = ref.split(Character.toString(NodeRef.PATH_SEPARATOR));

            Preconditions.checkArgument(allowOneLevel || tokens.length > 1,
                    "Ref must contain at least one slash (/) unless explicitly allowed.");

            for (String token : tokens) {
                Preconditions.checkArgument(token.length() > 0,
                        "Component of ref cannot be empty.");

                Preconditions.checkArgument(!token.startsWith(".") && !token.endsWith("."),
                        "Component of ref cannot begin or end with a dot (.).");

                Preconditions.checkArgument(!token.endsWith(".lock"),
                        "Component of ref cannot end with .lock.");

                Preconditions.checkArgument(Pattern.matches("[^\040\177 ~^:?\\*\\[]+", token),
                        "Component of ref cannot have ASCII control characters or any of the following: space, ~, ^, :, ?, *, [.");

                Preconditions.checkArgument(!token.contains(".."),
                        "Component of ref cannot have two consecutive dots (..) anywhere.");

                Preconditions.checkArgument(!token.contains("@{"),
                        "Component of ref cannot contain a sequence (@{) anywhere.");

                Preconditions.checkArgument(!token.equals("@"),
                        "Component of ref cannot be the single character (@).");

                Preconditions.checkArgument(!token.contains("\\"),
                        "Component of ref cannot contain a backslash (\\) anywhere.");
            }

        } catch (IllegalArgumentException e) {
            if (throwsException) {
                throw e;
            } else {
                return false;
            }
        }

        return true;
    }

}
