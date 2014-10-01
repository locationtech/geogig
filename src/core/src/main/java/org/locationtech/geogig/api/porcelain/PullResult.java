/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;

import com.google.common.base.Optional;

public class PullResult {

    private Ref oldRef = null;

    private Ref newRef = null;

    private String remoteName = null;

    private TransferSummary fetchResult = null;

    private Optional<MergeReport> mergeReport = Optional.absent();

    public String getRemoteName() {
        return remoteName;
    }

    void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
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
