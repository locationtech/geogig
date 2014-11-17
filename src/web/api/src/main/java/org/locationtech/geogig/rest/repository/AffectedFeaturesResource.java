/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Returns a list of all feature ids affected by a specified commit.
 */
public class AffectedFeaturesResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(new AffectedFeaturesRepresentation(request));
    }

    private static class AffectedFeaturesRepresentation extends OutputRepresentation {

        private Request request;

        public AffectedFeaturesRepresentation(Request request) {
            super(MediaType.TEXT_PLAIN);
            this.request = request;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            PrintWriter w = new PrintWriter(out);
            Form options = request.getResourceRef().getQueryAsForm();

            Optional<String> commit = Optional
                    .fromNullable(options.getFirstValue("commitId", null));

            Preconditions.checkState(commit.isPresent(), "No commit specified.");

            GeoGIG ggit = getGeogig(request).get();

            ObjectId commitId = ObjectId.valueOf(commit.get());

            RevCommit revCommit = ggit.getRepository().getCommit(commitId);

            if (revCommit.getParentIds() != null && revCommit.getParentIds().size() > 0) {
                ObjectId parentId = revCommit.getParentIds().get(0);
                final Iterator<DiffEntry> diff = ggit.command(DiffOp.class).setOldVersion(parentId)
                        .setNewVersion(commitId).call();

                while (diff.hasNext()) {
                    DiffEntry diffEntry = diff.next();
                    if (diffEntry.getOldObject() != null) {
                        w.write(diffEntry.getOldObject().getNode().getObjectId().toString() + "\n");
                    }
                }
                w.flush();
            }
        }
    }
}
