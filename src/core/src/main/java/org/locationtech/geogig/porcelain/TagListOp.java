/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BulkOpListener;

import com.google.common.collect.Streams;

/**
 * Returns a list of all tags
 * 
 */
public class TagListOp extends AbstractGeoGigOp<List<RevTag>> {

    protected @Override List<RevTag> _call() {
        Set<Ref> refs = command(ForEachRef.class).setPrefixFilter(Ref.TAGS_PREFIX).call();
        List<ObjectId> tagIds = refs.stream().map(Ref::getObjectId).collect(Collectors.toList());

        Iterator<RevTag> alltags = objectDatabase().getAll(tagIds, BulkOpListener.NOOP_LISTENER,
                RevTag.class);

        return Streams.stream(alltags).collect(Collectors.toList());
    }

}
