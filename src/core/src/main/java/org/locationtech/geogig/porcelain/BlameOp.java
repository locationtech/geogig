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

import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.DiffFeature;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.ResolveObjectType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.porcelain.BlameException.StatusCode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * Creates a report that contains information about who was the last to change each attribute in a
 * feature
 * 
 */
@CanRunDuringConflict
public class BlameOp extends AbstractGeoGigOp<BlameReport> {

    private String path;

    private ObjectId commit;

    /**
     * Sets the path of the feature to use
     * 
     * @param String path
     * @return
     */
    public BlameOp setPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * Sets the commit to blame from
     * 
     * @param ObjectId commit
     * @return
     */
    public BlameOp setCommit(ObjectId commit) {
        this.commit = commit;
        return this;
    }

    @Override
    protected BlameReport _call() {
        String fullPath = (commit != null ? commit.toString() : Ref.HEAD) + ":" + path;
        Optional<ObjectId> id = command(RevParse.class).setRefSpec(fullPath).call();
        if (!id.isPresent()) {
            throw new BlameException(StatusCode.FEATURE_NOT_FOUND);
        }
        TYPE type = command(ResolveObjectType.class).setObjectId(id.get()).call();
        if (!type.equals(TYPE.FEATURE)) {
            throw new BlameException(StatusCode.PATH_NOT_FEATURE);
        }
        Optional<RevFeatureType> featureType = command(ResolveFeatureType.class).setRefSpec(path)
                .call();

        BlameReport report = new BlameReport(featureType.get());

        Iterator<RevCommit> log = command(LogOp.class).addPath(path).setUntil(commit).call();
        RevCommit commit = log.next();
        RevObjectParse revObjectParse = command(RevObjectParse.class);
        DiffOp diffOp = command(DiffOp.class);
        DiffFeature diffFeature = command(DiffFeature.class);

        while (!report.isComplete()) {
            if (!log.hasNext()) {
                String refSpec = commit.getId().toString() + ":" + path;
                RevFeature feature = revObjectParse.setRefSpec(refSpec).call(RevFeature.class)
                        .get();
                report.setFirstVersion(feature, commit);
                break;
            }
            RevCommit commitB = log.next();
            try (AutoCloseableIterator<DiffEntry> diffs = diffOp.setNewVersion(commit.getId())
                    .setOldVersion(commitB.getId()).setReportTrees(false).call()) {
                while (diffs.hasNext()) {
                    DiffEntry diff = diffs.next();
                    if (path.equals(diff.newPath())) {
                        if (diff.isAdd()) {
                            String refSpec = commit.getId().toString() + ":" + path;
                            RevFeature feature = revObjectParse.setRefSpec(refSpec)
                                    .call(RevFeature.class).get();
                            report.setFirstVersion(feature, commit);
                            break;
                        }
                        FeatureDiff featureDiff = diffFeature
                                .setNewVersion(Suppliers.ofInstance(diff.getNewObject()))
                                .setOldVersion(Suppliers.ofInstance(diff.getOldObject())).call();
                        Map<PropertyDescriptor, AttributeDiff> attribDiffs = featureDiff.getDiffs();
                        Iterator<PropertyDescriptor> iter = attribDiffs.keySet().iterator();
                        while (iter.hasNext()) {
                            PropertyDescriptor key = iter.next();
                            Optional<?> value = Optional
                                    .fromNullable(attribDiffs.get(key).getNewValue());
                            String attribute = key.getName().toString();
                            report.addDiff(attribute, value, commit);
                        }
                    }

                }
            }
            commit = commitB;
        }
        return report;
    }
}
