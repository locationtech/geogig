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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.test.TestData;

import com.google.common.collect.Lists;

public class PRListOpTest {

    public @Rule TestSupport testSupport = new TestSupport();

    private TestData origin;

    public @Before void before() {
        origin = testSupport.newRepo("origin").loadDefaultData();
    }

    public @Test void testListPullRequests() {
        PR pr1 = createPr(1, "branch1");
        PR pr2 = createPr(2, "branch2");

        assertEquals(Lists.newArrayList(pr1, pr2), origin.getRepo().command(PRListOp.class).call());
    }

    private PR createPr(int id, String branch) {
        PRInitOp prinit = PRInitOp.builder()//
                .id(id)//
                .remoteURI(origin.getRepo().getLocation())//
                .remoteBranch(branch)//
                .targetBranch("master")//
                .title("PR " + id)//
                .description(null)//
                .build();
        prinit.setContext(origin.getRepo().context());
        return prinit.call();
    }
}
