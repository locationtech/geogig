/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.remoting;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.RefDiff.Type;
import org.locationtech.geogig.remotes.TransferSummary;

class FetchResultPrinter {

    public static void print(TransferSummary result, Console console) throws IOException {
        for (Entry<String, Collection<RefDiff>> entry : result.getRefDiffs().entrySet()) {
            console.println("From " + entry.getKey());

            for (RefDiff ref : entry.getValue()) {
                String line;
                if (ref.getType() == Type.CHANGED_REF) {
                    line = "   " + ref.getOldRef().getObjectId().toString().substring(0, 8) + ".."
                            + ref.getNewRef().getObjectId().toString().substring(0, 8) + "     "
                            + ref.getOldRef().localName() + " -> " + ref.getOldRef().getName();
                } else if (ref.getType() == Type.ADDED_REF) {
                    String reftype = (ref.getNewRef().getName().startsWith(Ref.TAGS_PREFIX)) ? "tag"
                            : "branch";
                    line = " * [new " + reftype + "]     " + ref.getNewRef().localName() + " -> "
                            + ref.getNewRef().getName();
                } else if (ref.getType() == Type.REMOVED_REF) {
                    line = " x [deleted]        (none) -> " + ref.getOldRef().getName();
                } else {
                    line = "   [deepened]       " + ref.getNewRef().localName();
                }
                console.println(line);
            }
        }
    }
}
