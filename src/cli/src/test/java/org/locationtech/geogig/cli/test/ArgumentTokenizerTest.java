/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.locationtech.geogig.cli.ArgumentTokenizer;

public class ArgumentTokenizerTest {

    @Test
    public void testTokenizer() {

        String s = "commit -m \"a message with blank spaces\"";
        String[] tokens = ArgumentTokenizer.tokenize(s);
        assertArrayEquals(new String[] { "commit", "-m", "\"a message with blank spaces\"" },
                tokens);
        s = "commit -m \"a message with line\nbreaks\"";
        tokens = ArgumentTokenizer.tokenize(s);
        assertArrayEquals(new String[] { "commit", "-m", "\"a message with line\nbreaks\"" },
                tokens);
        s = "reset HEAD~1 --hard";
        tokens = ArgumentTokenizer.tokenize(s);
        assertArrayEquals(new String[] { "reset", "HEAD~1", "--hard" }, tokens);
    }

}
