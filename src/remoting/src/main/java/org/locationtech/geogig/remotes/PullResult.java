/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes;

import java.util.Optional;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Remote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

public @Data @Builder @NoArgsConstructor @AllArgsConstructor class PullResult {

    private Ref oldRef;

    private Ref newRef;

    private TransferSummary fetchResult;

    private @Default Optional<MergeReport> mergeReport = Optional.empty();

    private Remote remote;

    public String getRemoteName() {
        return remote.getName();
    }
}
