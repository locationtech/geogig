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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.WriteTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.remote.BinaryPackedChanges;
import org.locationtech.geogig.remote.HttpFilteredDiffIterator;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.restlet.Context;
import org.restlet.Finder;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * Creates a new commit on the server with the changes provided by the client.
 */
public class ApplyChangesResource extends Finder {

    @Override
    public Resource findTarget(Request request, Response response) {
        return new ChangesResource(getContext(), request, response);
    }

    private class ChangesResource extends Resource {

        public ChangesResource(//
                Context context, //
                Request request, //
                Response response) //
        {
            super(context, request, response);
        }

        @Override
        public boolean allowPost() {
            return true;
        }

        @Override
        public void post(Representation entity) {
            InputStream input = null;
            ObjectId newCommitId = ObjectId.NULL;
            try {
                input = getRequest().getEntity().getStream();
                final GeoGIG ggit = getGeogig(getRequest()).get();

                final Repository repository = ggit.getRepository();

                // read in commit object
                final ObjectSerializingFactory factory = DataStreamSerializationFactoryV1.INSTANCE;
                ObjectReader<RevCommit> reader = factory.createCommitReader();
                RevCommit commit = reader.read(ObjectId.NULL, input); // I don't need to know the
                                                                      // original ObjectId

                // read in parents
                List<ObjectId> newParents = new LinkedList<ObjectId>();
                int numParents = input.read();
                for (int i = 0; i < numParents; i++) {
                    ObjectId parentId = readObjectId(input);
                    newParents.add(parentId);
                }

                // read in the changes
                BinaryPackedChanges unpacker = new BinaryPackedChanges(repository);
                Iterator<DiffEntry> changes = new HttpFilteredDiffIterator(input, unpacker);

                RevTree rootTree = RevTree.EMPTY;

                if (newParents.size() > 0) {
                    ObjectId mappedCommit = newParents.get(0);

                    Optional<ObjectId> treeId = repository.command(ResolveTreeish.class)
                            .setTreeish(mappedCommit).call();
                    if (treeId.isPresent()) {
                        rootTree = repository.getTree(treeId.get());
                    }
                }

                // Create new commit
                ObjectId newTreeId = repository.command(WriteTree.class)
                        .setOldRoot(Suppliers.ofInstance(rootTree))
                        .setDiffSupplier(Suppliers.ofInstance((Iterator<DiffEntry>) changes))
                        .dontMoveObjects()// HttpFilteredDiffIterator is "auto ingesting"
                        .call();

                CommitBuilder builder = new CommitBuilder(commit);

                builder.setParentIds(newParents);
                builder.setTreeId(newTreeId);

                RevCommit mapped = builder.build();
                repository.objectDatabase().put(mapped);
                newCommitId = mapped.getId();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            getResponse().setEntity(
                    new StringRepresentation(newCommitId.toString(), MediaType.TEXT_PLAIN));
        }

        private ObjectId readObjectId(final InputStream in) throws IOException {
            byte[] rawBytes = new byte[20];
            int amount = 0;
            int len = 20;
            int offset = 0;
            while ((amount = in.read(rawBytes, offset, len - offset)) != 0) {
                if (amount < 0)
                    throw new EOFException("Came to end of input");
                offset += amount;
                if (offset == len)
                    break;
            }
            ObjectId id = ObjectId.createNoClone(rawBytes);
            return id;
        }
    }
}
