/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import org.locationtech.geogig.model.RevCommit;

import com.google.common.base.Optional;

public class ValueAndCommit {

    public Optional<?> value;

    public RevCommit commit;

    public ValueAndCommit(Optional<?> value, RevCommit commit) {
        this.value = value;
        this.commit = commit;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(value.orNull()).append('/').append(commit.getId())
                .toString();
    }
}
