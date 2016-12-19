/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.List;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Returns a list of all tags
 * 
 */
public class TagListOp extends AbstractGeoGigOp<ImmutableList<RevTag>> {

    @Override
    protected ImmutableList<RevTag> _call() {
        List<Ref> refs = Lists
                .newArrayList(command(ForEachRef.class).setPrefixFilter(Ref.TAGS_PREFIX).call());
        List<RevTag> list = Lists.newArrayList();
        for (Ref ref : refs) {
            Optional<RevTag> tag = command(RevObjectParse.class).setObjectId(ref.getObjectId())
                    .call(RevTag.class);
            list.add(tag.get());
        }
        return ImmutableList.copyOf(list);
    }

}
