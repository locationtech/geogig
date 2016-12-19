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

import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Removes a tag
 * 
 */
public class TagRemoveOp extends AbstractGeoGigOp<RevTag> {

    private String name;

    /**
     * Executes the tag removal operation.
     * 
     * @return the tag to remove
     * 
     */
    @Override
    protected RevTag _call() throws RuntimeException {
        String fullPath = Ref.TAGS_PREFIX + name;
        Optional<RevObject> revTag = command(RevObjectParse.class).setRefSpec(fullPath).call();
        Preconditions.checkArgument(revTag.isPresent(), "Wrong tag name: " + name);
        Preconditions.checkArgument(revTag.get().getType().equals(RevObject.TYPE.TAG),
                name + " does not resolve to a tag");
        UpdateRef updateRef = command(UpdateRef.class).setName(fullPath).setDelete(true)
                .setReason("Delete tag " + name);
        Optional<Ref> tagRef = updateRef.call();
        checkState(tagRef.isPresent());
        return (RevTag) revTag.get();
    }

    public TagRemoveOp setName(String name) {
        this.name = name;
        return this;
    }
}
