/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.porcelain.BranchListOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.rest.RestletException;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.WebAPICommand;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BranchTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "branch";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Branch.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("list", "true", "remotes", "true", "branchName",
                "testbranch", "force", "true", "autoCheckout", "true", "orphan", "true", "source",
                "COMMIT_X");

        Branch op = (Branch) buildCommand(options);

        assertTrue(op.list);
        assertTrue(op.remotes);
        assertEquals("testbranch", op.branchName);
        assertEquals("COMMIT_X", op.source);

        // NOTE these options are disabled (not being set), amend the test case if enabled
        assertFalse(op.autoCheckout);
        assertFalse(op.force);
        assertFalse(op.orphan);
    }

    @Test
    public void createBranchEmptyHistory() {
        ParameterSet options = TestParams.of("branchName", "newBranch");
        Branch cmd = (Branch) buildCommand(options);
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("HEAD has no commits");
        cmd.run(testContext.get());
    }

    @Test
    public void createBranch() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        // have a commit to allow creating branch
        geogig.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("gabriel").call();
        geogig.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("gabriel@example.com").call();
        RevCommit commit = geogig.command(CommitOp.class).setAllowEmpty(true)
                .setMessage("initial commit").call();

        ParameterSet options = TestParams.of("branchName", "newBranch");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JSONObject obj = getJSONResponse().getJSONObject("response");
        assertTrue(obj.getBoolean("success"));
        assertTrue(obj.has("BranchCreated"));
        JSONObject branchResponse = obj.getJSONObject("BranchCreated");
        assertEquals("newBranch", branchResponse.get("name"));
        assertEquals(commit.getId().toString(), branchResponse.get("source"));

        ImmutableList<Ref> branchRefs = geogig.command(BranchListOp.class).call();
        assertEquals(2, branchRefs.size());
        List<String> branchNames = Lists.transform(branchRefs, (r) -> r.getName());
        assertTrue(branchNames.toString(), branchNames.contains("refs/heads/newBranch"));
    }
    
    @Test
    public void testRequireRepository() {
        testContext.createUninitializedRepo();
        ParameterSet options = TestParams.of("branchName", "newBranch");
        WebAPICommand cmd = buildCommand(options);

        ex.expect(RestletException.class);
        ex.expectMessage("Repository not found.");
        cmd.run(testContext.get());
    }
}
