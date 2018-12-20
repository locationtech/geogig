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

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.Exists;
import org.locationtech.geogig.spring.dto.Merge;
import org.locationtech.geogig.spring.dto.MergeFeatureRequest;
import org.locationtech.geogig.spring.dto.Parents;
import org.locationtech.geogig.spring.dto.RepositoryInfo;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Service for Repository stats.
 */
@Service("repositoryService")
public class RepositoryService extends AbstractRepositoryService {

    public RepositoryInfo getRepositoryInfo(RepositoryProvider provider, String repoName) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null && repository.getLocation() != null) {
            RepositoryInfo repoInfo = new RepositoryInfo().setName(repoName).
                    // the location may need to be masked. Let the Provider do that.
                    setLocation(provider.getMaskedLocationString(repository, repoName)).
                    setId(provider.getRepositoryId(repoName));
            return repoInfo;
        }
        return null;
    }

    public ImmutableList<Ref> getBranchList(RepositoryProvider provider, String repoName,
            boolean includeRemotes) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            return repository.command(BranchListOp.class).setRemotes(includeRemotes).call();
        }
        return ImmutableList.of();
    }

    public ImmutableList<RevTag> getTagList(RepositoryProvider provider, String repoName) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            return repository.command(TagListOp.class).call();
        }
        return ImmutableList.of();
    }

    public Ref getCurrentHead(RepositoryProvider provider, String repoName) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            return repository.command(RefParse.class).setName(Ref.HEAD).call().get();
        }
        return null;
    }

    public RevObject getRevObject(RepositoryProvider provider, String repoName, ObjectId oid) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            return repository.objectDatabase().get(oid);
        }
        return null;
    }

    public Integer getDepth(RepositoryProvider provider, String repoName, ObjectId commitId) {
        Optional<Integer> depth = Optional.absent();
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            if (commitId != null) {
                depth = Optional.of(repository.graphDatabase().getDepth(commitId));
            } else {
                depth = repository.getDepth();
            }
        }
        return depth.orNull();
    }

    public AutoCloseableIterator<DiffEntry> getAffectedFeatures(RepositoryProvider provider,
            String repoName, ObjectId commitId) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            final RevCommit revCommit = repository.getCommit(commitId);
            if (revCommit.getParentIds() != null && revCommit.getParentIds().size() > 0) {
                ObjectId parentId = revCommit.getParentIds().get(0);
                return repository.command(DiffOp.class)
                        .setOldVersion(parentId).setNewVersion(commitId).call();
            }
        }
        return AutoCloseableIterator.emptyIterator();
    }

    private Optional<NodeRef> parseID(ObjectId commitId, String path, Repository geogig) {
        Optional<RevObject> object = geogig.command(RevObjectParse.class).setObjectId(commitId)
                .call();
        RevCommit commit = null;
        if (object.isPresent() && object.get() instanceof RevCommit) {
            commit = (RevCommit) object.get();
        } else {
            throw new CommandSpecException(
                    "Couldn't resolve id: " + commitId.toString() + " to a commit");
        }

        object = geogig.command(RevObjectParse.class).setObjectId(commit.getTreeId()).call();

        if (object.isPresent()) {
            RevTree tree = (RevTree) object.get();
            return geogig.command(FindTreeChild.class).setParent(tree).setChildPath(path).call();
        } else {
            throw new CommandSpecException("Couldn't resolve commit's treeId");
        }
    }

    private int getDescriptorIndex(String key, ImmutableList<PropertyDescriptor> properties) {
        for (int i = 0; i < properties.size(); i++) {
            PropertyDescriptor prop = properties.get(i);
            if (prop.getName().toString().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public RevFeature mergeFeatures(RepositoryProvider provider, String repoName,
            MergeFeatureRequest request) {
        // validty check, shouldn't hit this unless the controller isn't catching these
        if (request == null || request.getMerges() == null || request.getOurs() == null ||
                request.getPath() == null || request.getTheirs() == null) {
            throw new IllegalArgumentException("Invalid POST data.");
        }
        // get the repo
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            // ours and thiers
            RevFeature ourFeature = null;
            RevFeatureType ourFeatureType = null;
            RevFeature theirFeature = null;
            RevFeatureType theirFeatureType = null;
            // get our Node
            Optional<NodeRef> ourNode = parseID(ObjectId.valueOf(request.getOurs()),
                    request.getPath(), repository);
            if (ourNode.isPresent()) {
                // get the RevObject for our Node
                Optional<RevObject> object = repository.command(RevObjectParse.class)
                        .setObjectId(ourNode.get().getObjectId()).call();
                Preconditions.checkState(
                        object.isPresent() && object.get() instanceof RevFeature);

                ourFeature = (RevFeature) object.get();

                object = repository.command(RevObjectParse.class)
                        .setObjectId(ourNode.get().getMetadataId()).call();
                Preconditions.checkState(
                        object.isPresent() && object.get() instanceof RevFeatureType);

                ourFeatureType = (RevFeatureType) object.get();
            }
            // get their node
            Optional<NodeRef> theirNode = parseID(ObjectId.valueOf(request.getTheirs()),
                    request.getPath(), repository);
            if (theirNode.isPresent()) {
                Optional<RevObject> object = repository.command(RevObjectParse.class)
                        .setObjectId(theirNode.get().getObjectId()).call();
                Preconditions.checkState(
                        object.isPresent() && object.get() instanceof RevFeature);

                theirFeature = (RevFeature) object.get();

                object = repository.command(RevObjectParse.class)
                        .setObjectId(theirNode.get().getMetadataId()).call();
                Preconditions.checkState(
                        object.isPresent() && object.get() instanceof RevFeatureType);

                theirFeatureType = (RevFeatureType) object.get();
            }
            // ensure feature types are not null
            Preconditions.checkState(ourFeatureType != null || theirFeatureType != null);
            // get the feature builder for the feature to be merged
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(
                    (SimpleFeatureType) (ourFeatureType != null ? ourFeatureType.type() :
                             theirFeatureType.type()));
            // get an iterator of the feature properties
            ImmutableList<PropertyDescriptor> descriptors = (ourFeatureType == null ?
                    theirFeatureType : ourFeatureType).descriptors();
            // configure the builder
            for (Merge merge : request.getMerges()) {
                int descriptorIndex = getDescriptorIndex(merge.getAttribute(), descriptors);
                if (descriptorIndex > -1) {
                    // get the feature property descriptor
                    PropertyDescriptor descriptor = descriptors.get(descriptorIndex);
                    // determine "ours", "theirs" or "value" (should be mutually exclusive)
                    if (Boolean.TRUE.equals(merge.getOurs())) {
                        // take "ours" for this property
                        featureBuilder.set(descriptor.getName(), ourFeature == null ? null :
                                 ourFeature.get(descriptorIndex).orNull());

                    } else if (Boolean.TRUE.equals(merge.getTheirs())) {
                        // take "theirs" for this property
                        featureBuilder.set(descriptor.getName(), theirFeature == null ? null :
                                theirFeature.get(descriptorIndex).orNull());
                    } else {
                        // take "value", even if it's null
                        featureBuilder.set(descriptor.getName(), merge.getValue());
                    }
                }
            }
            // merge the feature
            SimpleFeature feature = featureBuilder
                    .buildFeature(NodeRef.nodeFromPath(request.getPath()));
            RevFeature revFeature = RevFeature.builder().build(feature);
            repository.objectDatabase().put(revFeature);
            return revFeature;
        }
        return null;
    }

    public Exists blobExists(RepositoryProvider provider, String repoName, ObjectId oid) {
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            return new Exists().setExists(repository.blobExists(oid));
        }
        return new Exists().setExists(false);
    }

    public Parents getParents(RepositoryProvider provider, String repoName, ObjectId oid) {
        Repository repository = getRepository(provider, repoName);
        Parents parents = new Parents();
        if (repository != null && oid != null) {
            parents.setParents(repository.graphDatabase().getParents(oid));
        }
        return parents;
    }
}
