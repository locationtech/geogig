/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.test.TestData;

public class PRResolveOpTest {

    public @Rule TestSupport testSupport = new TestSupport();

    private TestData origin;

    public @Before void before() {
        origin = testSupport.newRepo("origin");
    }

    public @Test void testResolve() {
        origin.loadDefaultData()//
                .checkout("branch1")//
                .branchAndCheckout("issuerBranch")//
                .remove(TestData.line2).add().commit("remove line2");

        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(origin.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("branch1")//
                .title("first PR")//
                .description(null)//
                .build();
        prinit.setContext(origin.getRepo().context());
        PR request = prinit.call();

        Optional<PR> resolved = origin.getRepo().command(PRFindOp.class).setId(request.getId())
                .call();
        assertTrue(resolved.isPresent());
        assertEquals(request, resolved.get());

        assertFalse(origin.getRepo().command(PRFindOp.class).setId(1 + request.getId()).call()
                .isPresent());

    }
}
