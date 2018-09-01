/* Copyright (c) 2018-present Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Optional;

import org.locationtech.geogig.model.Ref;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public @Value @Builder class BranchConfig {

    private @NonNull Ref branch;

    private @NonNull Optional<String> remoteName, remoteBranch;

    private @NonNull Optional<String> description;

}
