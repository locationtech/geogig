/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SymRefTest {

    @Test
    public void testSymRef() {
        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", ObjectId.forString("Test Commit"));

        SymRef symRef = new SymRef("TestRef", testRef);

        assertEquals(testRef.getName(), symRef.getTarget());

        String symRefString = symRef.toString();

        assertEquals("SymRef[TestRef -> " + "Ref[" + testRef.getName() + " -> "
                + testRef.getObjectId().toString() + "]]", symRefString);
    }
}
