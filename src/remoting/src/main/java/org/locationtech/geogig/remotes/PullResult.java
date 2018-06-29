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

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Optional;

public class PullResult {

    private Ref oldRef = null;

    private Ref newRef = null;

    private TransferSummary fetchResult = null;

    private Optional<MergeReport> mergeReport = Optional.absent();

    private Remote remote;

    public String getRemoteName() {
        return remote.getName();
    }

    public Remote getRemote() {
        return remote;
    }

    void setRemote(Remote remote) {
        this.remote = remote;
    }

    public TransferSummary getFetchResult() {
        return fetchResult;
    }

    void setFetchResult(TransferSummary fetchResult) {
        this.fetchResult = fetchResult;
    }

    public Ref getOldRef() {
        return oldRef;
    }

    void setOldRef(Ref oldRef) {
        this.oldRef = oldRef;
    }

    public Ref getNewRef() {
        return newRef;
    }

    void setNewRef(Ref newRef) {
        this.newRef = newRef;
    }

    public Optional<MergeReport> getMergeReport() {
        return mergeReport;
    }

    void setMergeReport(Optional<MergeReport> mergeReport) {
        this.mergeReport = mergeReport;
    }
}
