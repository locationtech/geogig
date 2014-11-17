/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.FetchResult;
import org.locationtech.geogig.api.porcelain.FetchResult.ChangedRef;
import org.locationtech.geogig.api.porcelain.FetchResult.ChangedRef.ChangeTypes;

class FetchResultPrinter {

    public static void print(FetchResult result, ConsoleReader console) throws IOException {
        for (Entry<String, List<ChangedRef>> entry : result.getChangedRefs().entrySet()) {
            console.println("From " + entry.getKey());

            for (ChangedRef ref : entry.getValue()) {
                String line;
                if (ref.getType() == ChangeTypes.CHANGED_REF) {
                    line = "   " + ref.getOldRef().getObjectId().toString().substring(0, 7) + ".."
                            + ref.getNewRef().getObjectId().toString().substring(0, 7) + "     "
                            + ref.getOldRef().localName() + " -> " + ref.getOldRef().getName();
                } else if (ref.getType() == ChangeTypes.ADDED_REF) {
                    String reftype = (ref.getNewRef().getName().startsWith(Ref.TAGS_PREFIX)) ? "tag"
                            : "branch";
                    line = " * [new " + reftype + "]     " + ref.getNewRef().localName() + " -> "
                            + ref.getNewRef().getName();
                } else if (ref.getType() == ChangeTypes.REMOVED_REF) {
                    line = " x [deleted]        (none) -> " + ref.getOldRef().getName();
                } else {
                    line = "   [deepened]       " + ref.getNewRef().localName();
                }
                console.println(line);
            }
        }
    }
}
