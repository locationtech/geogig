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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PRListOp extends PRCommand<List<PR>> {

    protected @Override List<PR> _call() {
        IntStream ids = configDatabase().getAllSubsections("pr").stream()
                .mapToInt(s -> Integer.valueOf(s)).sorted();

        Stream<PR> prstream = ids.parallel()
                .mapToObj(prId -> command(PRFindOp.class).setId(prId).call().orElse(null))
                .filter(r -> r != null);

        List<PR> prs = prstream.collect(Collectors.toList());
        return prs;
    }

}
