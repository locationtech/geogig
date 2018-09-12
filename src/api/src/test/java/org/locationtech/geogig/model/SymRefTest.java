/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SymRefTest {

    @Test
    public void testSymRef() {
        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"));

        SymRef symRef = new SymRef("TestRef", testRef);

        assertEquals(testRef.getName(), symRef.getTarget());

        String symRefString = symRef.toString();

        assertEquals("TestRef -> " + "[" + testRef.getName() + " -> "
                + testRef.getObjectId().toString() + "]", symRefString);
    }
}
