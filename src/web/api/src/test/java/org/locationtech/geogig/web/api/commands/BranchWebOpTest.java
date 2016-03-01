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
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.BranchListOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.WebAPICommand;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BranchWebOpTest {

    @Rule
    public TestContext context = new TestContext();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Test
    public void testSPI() {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = CommandBuilder.build("branch", options);
        assertTrue(cmd instanceof BranchWebOp);
    }

    @Test
    public void testBuildTxId() {
        UUID txId = UUID.randomUUID();
        ParameterSet options = TestParams.of("transactionId", txId.toString());
        BranchWebOp cmd = (BranchWebOp) CommandBuilder.build("branch", options);
        assertEquals(txId, cmd.getTransactionId());
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("list", "true", "remotes", "true", "branchName",
                "testbranch", "force", "true", "autoCheckout", "true", "orphan", "true",
                "source", "COMMIT_X");

        WebAPICommand cmd = CommandBuilder.build("branch", options);

        BranchWebOp op = (BranchWebOp) cmd;
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
        BranchWebOp cmd = (BranchWebOp) CommandBuilder.build("branch", options);
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("HEAD has no commits");
        cmd.run(context.get());
    }

    @Test
    public void createBranch() {
        GeoGIG geogig = context.get().getGeoGIG();
        // have a commit to allow creating branch
        geogig.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("gabriel").call();
        geogig.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("gabriel@example.com").call();
        geogig.command(CommitOp.class).setAllowEmpty(true).setMessage("initial commit").call();

        ParameterSet options = TestParams.of("branchName", "newBranch");
        BranchWebOp cmd = (BranchWebOp) CommandBuilder.build("branch", options);
        cmd.run(context.get());

        ImmutableList<Ref> branchRefs = geogig.command(BranchListOp.class).call();
        assertEquals(2, branchRefs.size());
        List<String> branchNames = Lists.transform(branchRefs, (r) -> r.getName());
        assertTrue(branchNames.toString(), branchNames.contains("refs/heads/newBranch"));
    }
}
