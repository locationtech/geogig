/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;

/**
 * A tuple representing the result of some computation against the diff between two trees, holding
 * the result of the left and right sides of the computation, and optionally the merged/unioned
 * result.
 * 
 * @param <T>
 */
public class DiffSummary<T, M> {

    private T left;

    private T right;

    private M merged;

    public DiffSummary(T left, T right, @Nullable M merged) {
        this.left = left;
        this.right = right;
        this.merged = merged;
    }

    public T getLeft() {
        return left;
    }

    public T getRight() {
        return right;
    }

    public Optional<M> getMergedResult() {
        return Optional.fromNullable(merged);
    }
}
