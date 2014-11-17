/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.RebaseOp;
import org.locationtech.geogig.osm.internal.log.OSMLogEntry;
import org.locationtech.geogig.osm.internal.log.ReadOSMFilterFile;
import org.locationtech.geogig.osm.internal.log.ReadOSMLogEntries;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Updates the OSM data using the existing filter.
 * 
 */
public class OSMUpdateOp extends AbstractGeoGigOp<Optional<OSMReport>> {

    private String apiUrl;

    private String message;

    private boolean rebase;

    /**
     * If specified, use rebase instead of merge after updating the data.
     * 
     * @param rebase whether or not to rebase changes
     * @return {@code this}
     */
    public OSMUpdateOp setRebase(boolean rebase) {
        this.rebase = rebase;
        return this;
    }

    /**
     * Sets the URL of the OSM API.
     * 
     * @param url the url of the OSM API
     * @return {@code this}
     */
    public OSMUpdateOp setAPIUrl(String url) {
        this.apiUrl = url;
        return this;
    }

    /**
     * Sets the commit message to use when committing the updates.
     * 
     * @param message the commit message to use
     * @return {@code this}
     */
    public OSMUpdateOp setMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Executes the {@code OSMUpdateOp} operation.
     * 
     * @return a {@link OSMDownloadReport} of the operation
     */
    @Override
    protected Optional<OSMReport> _call() {
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't update.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't update from detached HEAD");

        List<OSMLogEntry> entries = command(ReadOSMLogEntries.class).call();
        checkArgument(!entries.isEmpty(), "Not in a geogig repository with OSM data");

        Iterator<RevCommit> log = command(LogOp.class).setFirstParentOnly(false)
                .setTopoOrder(false).call();

        RevCommit lastCommit = null;
        OSMLogEntry lastEntry = null;
        while (log.hasNext()) {
            RevCommit commit = log.next();
            for (OSMLogEntry entry : entries) {
                if (entry.getId().equals(commit.getTreeId())) {
                    lastCommit = commit;
                    lastEntry = entry;
                    break;
                }
            }
            if (lastCommit != null) {
                break;
            }
        }
        checkNotNull(lastCommit, "The current branch does not contain OSM data");

        command(BranchCreateOp.class).setSource(lastCommit.getId().toString())
                .setName(OSMUtils.OSM_FETCH_BRANCH).setAutoCheckout(true).setForce(true).call();

        Optional<String> filter = command(ReadOSMFilterFile.class).setEntry(lastEntry).call();

        Preconditions.checkState(filter.isPresent(), "Filter file not found");

        message = message == null ? "Updated OSM data" : message;
        Optional<OSMReport> report = command(OSMImportOp.class).setMessage(message)
                .setFilter(filter.get()).setDataSource(apiUrl)
                .setProgressListener(getProgressListener()).call();

        if (!report.isPresent()) {
            return report;
        }

        command(CheckoutOp.class).setSource(((SymRef) currHead.get()).getTarget()).call();

        Optional<Ref> upstreamRef = command(RefParse.class).setName(OSMUtils.OSM_FETCH_BRANCH)
                .call();
        Supplier<ObjectId> commitSupplier = Suppliers.ofInstance(upstreamRef.get().getObjectId());
        if (rebase) {
            getProgressListener().setDescription("Rebasing updated features...");
            command(RebaseOp.class).setUpstream(commitSupplier)
                    .setProgressListener(getProgressListener()).call();
        } else {
            getProgressListener().setDescription("Merging updated features...");
            command(MergeOp.class).addCommit(commitSupplier)
                    .setProgressListener(getProgressListener()).call();
        }
        return report;
    }
}
