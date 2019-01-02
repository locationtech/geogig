/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.WriteTree;
import org.locationtech.geogig.remote.http.BinaryPackedChanges;
import org.locationtech.geogig.remote.http.HttpFilteredDiffIterator;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * Copy of the Restlet version of ApplyChangesResource code.
 */
@Service("legacyApplyChangesService")
public class LegacyApplyChangesService extends AbstractRepositoryService {

    public RevCommit applyChanges(RepositoryProvider provider, String repoName,
            InputStream input) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            try {
                // read in commit object
                final RevObjectSerializer serializer = DataStreamRevObjectSerializerV1.INSTANCE;
                RevCommit commit = (RevCommit) serializer.read(ObjectId.NULL, input); // I don't
                                                                                      // need to
                                                                                      // know the
                // original ObjectId

                // read in parents
                List<ObjectId> newParents = new LinkedList<>();
                int numParents = input.read();
                for (int i = 0; i < numParents; i++) {
                    ObjectId parentId = readObjectId(input);
                    newParents.add(parentId);
                }

                // read in the changes
                BinaryPackedChanges unpacker = new BinaryPackedChanges(repository);
                try (AutoCloseableIterator<DiffEntry> changes = new HttpFilteredDiffIterator(input,
                        unpacker)) {
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
                            .setDiffSupplier(Suppliers.ofInstance(changes)).call();

                    RevCommitBuilder builder = RevCommit.builder().init(commit);

                    builder.parentIds(newParents);
                    builder.treeId(newTreeId);

                    RevCommit mapped = builder.build();
                    repository.objectDatabase().put(mapped);
                    return mapped;
                }

            } catch (IOException e) {
                throw new CommandSpecException(e.getMessage(), HttpStatus.BAD_REQUEST, e);
            }
        }
        return null;
    }

    private ObjectId readObjectId(final InputStream in) throws IOException {
        byte[] rawBytes = new byte[20];
        int amount;
        int len = 20;
        int offset = 0;
        while ((amount = in.read(rawBytes, offset, len - offset)) != 0) {
            if (amount < 0) {
                throw new EOFException("Came to end of input");
            }
            offset += amount;
            if (offset == len) {
                break;
            }
        }
        ObjectId id = ObjectId.create(rawBytes);
        return id;
    }
}
