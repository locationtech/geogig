/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.BranchListOp;
import org.locationtech.geogig.api.porcelain.TagListOp;
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
import com.google.common.collect.ImmutableList;

/**
 *
 */
public class ManifestResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(new ManifestRepresentation(request));
    }

    private static class ManifestRepresentation extends OutputRepresentation {

        private Request request;

        public ManifestRepresentation(Request request) {
            super(MediaType.TEXT_PLAIN);
            this.request = request;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            PrintWriter w = new PrintWriter(out);

            Optional<GeoGIG> geogig = getGeogig(request);
            Preconditions.checkState(geogig.isPresent());
            GeoGIG ggit = geogig.get();

            Form options = request.getResourceRef().getQueryAsForm();

            boolean remotes = Boolean.valueOf(options.getFirstValue("remotes", "false"));

            ImmutableList<Ref> refs = ggit.command(BranchListOp.class).setRemotes(remotes).call();
            ImmutableList<RevTag> tags = ggit.command(TagListOp.class).call();

            // Print out HEAD first
            final Ref currentHead = ggit.command(RefParse.class).setName(Ref.HEAD).call().get();
            if (!currentHead.getObjectId().equals(ObjectId.NULL)) {
                w.write(currentHead.getName() + " ");
                if (currentHead instanceof SymRef) {
                    w.write(((SymRef) currentHead).getTarget());
                }
                w.write(" ");
                w.write(currentHead.getObjectId().toString());
                w.write("\n");
            }

            // Print out the local branches
            for (Ref ref : refs) {
                if (!ref.getObjectId().equals(ObjectId.NULL)) {
                    w.write(ref.getName());
                    w.write(" ");
                    w.write(ref.getObjectId().toString());
                    w.write("\n");
                }
            }
            // Print out the tags
            for (RevTag tag : tags) {
                w.write("refs/tags/");
                w.write(tag.getName());
                w.write(" ");
                w.write(tag.getId().toString());
                w.write("\n");
            }
            w.flush();
        }
    }
}
