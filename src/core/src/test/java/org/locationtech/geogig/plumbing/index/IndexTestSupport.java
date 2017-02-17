/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.google.common.collect.Lists;

public class IndexTestSupport {

    public static void verifyIndex(GeoGIG geogig, ObjectId indexTreeId, ObjectId canonicalTreeId,
            String... extraAttributes) {
        Iterator<NodeRef> canonicalFeatures = geogig.command(LsTreeOp.class)
                .setReference(canonicalTreeId.toString()).setStrategy(Strategy.FEATURES_ONLY)
                .call();

        Iterator<NodeRef> indexFeatures = geogig.command(LsTreeOp.class)
                .setReference(indexTreeId.toString()).setStrategy(Strategy.FEATURES_ONLY)
                .setSource(geogig.getRepository().indexDatabase()).call();

        List<NodeRef> canonicalFeaturesList = Lists.newArrayList(canonicalFeatures);
        while (indexFeatures.hasNext()) {
            NodeRef indexFeature = indexFeatures.next();
            if (extraAttributes.length > 0) {
                Map<String, Object> featureExtraData = indexFeature.getNode().getExtraData();
                assertNotNull("Node has no extra data: " + indexFeature.getNode(),
                        featureExtraData);
                Map<String, Object> featureExtraAttributes = IndexInfo
                        .getMaterializedAttributes(indexFeature.getNode());
                assertEquals(extraAttributes.length, featureExtraAttributes.size());
                for (String attribute : extraAttributes) {
                    assertTrue(featureExtraAttributes.containsKey(attribute));
                    featureExtraAttributes.remove(attribute);
                }
                assertTrue(featureExtraAttributes.isEmpty());
            }
            assertTrue(canonicalFeaturesList.contains(indexFeature));
            canonicalFeaturesList.remove(indexFeature);
        }
        assertTrue(canonicalFeaturesList.isEmpty());
    }

}
