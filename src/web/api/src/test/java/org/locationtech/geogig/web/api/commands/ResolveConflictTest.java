/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.repository.GeogigTransaction;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class ResolveConflictTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "resolveconflict";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return ResolveConflict.class;
    }

    @Test
    public void testBuildParameters() {
        ObjectId someObjectId = ObjectId.forString("object");
        ParameterSet options = TestParams.of("path", "some/path", "objectid",
                someObjectId.toString());

        ResolveConflict op = (ResolveConflict) buildCommand(options);
        assertEquals("some/path", op.path);
        assertEquals(someObjectId, op.objectId);
    }

    @Test
    public void testResolveConflict() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1).getId();
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point1_modified);
        testData.add();
        geogig.command(CommitOp.class).setMessage("modify point1").call();
        testData.checkout("branch1");
        testData.remove(TestData.point1);
        testData.add();
        RevCommit branch1Commit = geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        try {
            transaction.command(MergeOp.class).addCommit(branch1Commit.getId()).call();
        } catch (MergeConflictsException e) {
            // This is expected.
        }

        ParameterSet options = TestParams.of("path", path, "objectid", point1_id.toString(),
                "transactionId", transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("Success", response.getString("Add"));
    }

}
