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

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BulkOpListener;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

/**
 * Returns a list of all tags
 * 
 */
public class TagListOp extends AbstractGeoGigOp<ImmutableList<RevTag>> {

    @Override
    protected ImmutableList<RevTag> _call() {
        List<Ref> refs = newArrayList(
                command(ForEachRef.class).setPrefixFilter(Ref.TAGS_PREFIX).call());

        // (r) -> r.getObjectId()
        Function<Ref, ObjectId> fn  =  new Function<Ref, ObjectId>() {
            @Override
            public ObjectId apply(Ref ref) {
                return ref.getObjectId();
            }};

        List<ObjectId> tagIds = transform(refs, fn);

        Iterator<RevTag> alltags;
        alltags = objectDatabase().getAll(tagIds, BulkOpListener.NOOP_LISTENER, RevTag.class);

        ImmutableList<RevTag> res = ImmutableList.copyOf(alltags);

        return res;
    }

}
