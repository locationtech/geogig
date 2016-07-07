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

import java.util.UUID;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class AddTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "add";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Add.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("path", "points", "transactionId", UUID.randomUUID()
                .toString());

        Add op = (Add) buildCommand(options);
        assertEquals("points", op.path);
    }
    
    @Test
    public void testAddAll() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        GeogigTransaction transaction = geogig
                .command(TransactionBegin.class).call();
        testData.setTransaction(transaction);
        StagingArea staging = transaction.index();
        testData.insert(TestData.point1);
        assertEquals(0, staging.countStaged(null).featureCount());
        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());

        buildCommand(options).run(testContext.get());

        assertEquals(1, staging.countStaged(null).featureCount());
    }

    @Test
    public void testAddPath() throws Exception{
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        GeogigTransaction transaction = geogig
                .command(TransactionBegin.class).call();
        testData.setTransaction(transaction);
        StagingArea staging = transaction.index();
        testData.insert(TestData.point1);
        testData.insert(TestData.point2);
        assertEquals(0, staging.countStaged(null).featureCount());
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(), TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "transactionId",
                transaction.getTransactionId().toString());

        buildCommand(options).run(testContext.get());

        assertEquals(1, staging.countStaged(null).featureCount());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("Success", response.getString("Add"));
    }
}
