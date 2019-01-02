/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CheckSparsePath;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.remotes.internal.AbstractMappedRemoteRepo;
import org.locationtech.geogig.remotes.internal.FilteredDiffIterator;
import org.locationtech.geogig.remotes.internal.RepositoryWrapper;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryFilter;
import org.locationtech.geogig.repository.impl.RepositoryFilter.FilterDescription;
import org.locationtech.geogig.repository.impl.RepositoryImpl;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Provides a means of communicating between a local sparse clone and a remote http full repository.
 * 
 * @see AbstractMappedRemoteRepo
 */
public class HttpMappedRemoteRepo extends AbstractMappedRemoteRepo {

    private URL repositoryURL;

    /**
     * Constructs a new {@code HttpMappedRemoteRepo}.
     * 
     * @param repositoryURL the URL of the repository
     * @param localRepository the local sparse repository
     */
    public HttpMappedRemoteRepo(Remote remote, URL repositoryURL) {
        super(remote);
        String url = repositoryURL.toString();
        if (url.endsWith("/")) {
            url = url.substring(0, url.lastIndexOf('/'));
        }
        try {
            this.repositoryURL = new URL(url);
        } catch (MalformedURLException e) {
            this.repositoryURL = repositoryURL;
        }
    }

    /**
     * Currently does nothing for HTTP Remote.
     */
    @Override
    public void open() {
    }

    /**
     * Currently does nothing for HTTP Remote.
     */
    @Override
    public void close() {
    }

    /**
     * List the mapped versions of the remote's {@link Ref refs}. For example, if the remote ref
     * points to commit A, the returned ref will point to the commit that A is mapped to.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    @Override
    public ImmutableSet<Ref> listRefs(Repository local, boolean getHeads, boolean getTags) {
        HttpURLConnection connection = null;
        ImmutableSet.Builder<Ref> builder = new ImmutableSet.Builder<Ref>();
        try {
            String expanded = repositoryURL.toString() + "/repo/manifest";

            connection = (HttpURLConnection) new URL(expanded).openConnection();
            connection.setRequestMethod("GET");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            // Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            try {
                while ((line = rd.readLine()) != null) {
                    if ((getHeads && line.startsWith("refs/heads"))
                            || (getTags && line.startsWith("refs/tags"))) {
                        Ref remoteRef = HttpUtils.parseRef(line);
                        Ref newRef = remoteRef;
                        if (!(newRef instanceof SymRef)
                                && local.graphDatabase().exists(remoteRef.getObjectId())) {
                            ObjectId mappedCommit = local.graphDatabase()
                                    .getMapping(remoteRef.getObjectId());
                            if (mappedCommit != null) {
                                newRef = new Ref(remoteRef.getName(), mappedCommit);
                            }
                        }
                        builder.add(newRef);
                    }
                }
            } finally {
                rd.close();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            HttpUtils.consumeErrStreamAndCloseConnection(connection);
        }
        return builder.build();
    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    @Override
    public Optional<Ref> headRef() {
        HttpURLConnection connection = null;
        Ref headRef = null;
        try {
            String expanded = repositoryURL.toString() + "/repo/manifest";

            connection = (HttpURLConnection) new URL(expanded).openConnection();
            connection.setRequestMethod("GET");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            // Get Response
            InputStream is = connection.getInputStream();
            try {
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String line;

                while ((line = rd.readLine()) != null) {
                    if (line.startsWith("HEAD")) {
                        headRef = HttpUtils.parseRef(line);
                    }
                }
                rd.close();
            } finally {
                is.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            HttpUtils.consumeErrStreamAndCloseConnection(connection);
        }
        return Optional.fromNullable(headRef);
    }

    /**
     * Delete a {@link Ref} from the remote repository.
     * 
     * @param refspec the ref to delete
     */
    @Override
    public Optional<Ref> deleteRef(String refspec) {
        return updateRemoteRef(refspec, null, true);
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    @Override
    protected RepositoryWrapper getRemoteWrapper() {
        return new HttpRepositoryWrapper(repositoryURL);
    }

    /**
     * Gets all of the changes from the target commit that should be applied to the sparse clone.
     * 
     * @param commit the commit to get changes from
     * @return an iterator for changes that match the repository filter
     */
    @Override
    protected FilteredDiffIterator getFilteredChanges(final Repository local, RevCommit commit) {
        // Get affected features
        ImmutableList<ObjectId> affectedFeatures = HttpUtils.getAffectedFeatures(repositoryURL,
                commit.getId());
        // Create a list of features I have
        List<ObjectId> tracked = new LinkedList<ObjectId>();
        for (ObjectId id : affectedFeatures) {
            if (local.blobExists(id)) {
                tracked.add(id);
            }
        }
        // Get changes from commit, pass filter and my list of features
        final JsonObject message = createFetchMessage(local, commit.getId(), tracked);
        final URL resourceURL;
        try {
            resourceURL = new URL(repositoryURL.toString() + "/repo/filteredchanges");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        final Gson gson = new Gson();
        final HttpURLConnection connection;
        final OutputStream out;
        final Writer writer;
        try {
            connection = (HttpURLConnection) resourceURL.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            out = connection.getOutputStream();
            writer = new OutputStreamWriter(out);
            gson.toJson(message, writer);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final InputStream in;
        try {
            in = connection.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BinaryPackedChanges unpacker = new BinaryPackedChanges(local);

        return new HttpFilteredDiffIterator(in, unpacker);
    }

    private JsonObject createFetchMessage(Repository local, ObjectId commitId,
            List<ObjectId> tracked) {
        JsonObject message = new JsonObject();
        JsonArray trackedArray = new JsonArray();
        for (ObjectId id : tracked) {
            trackedArray.add(new JsonPrimitive(id.toString()));
        }
        message.add("commitId", new JsonPrimitive(commitId.toString()));
        message.add("tracked", trackedArray);
        JsonArray filterArray = new JsonArray();
        Optional<RepositoryFilter> filter = RepositoryImpl.getFilter(local);
        checkState(filter.isPresent(), "No filter found for sparse clone.");
        ImmutableList<FilterDescription> repoFilters = filter.get().getFilterDescriptions();
        for (FilterDescription description : repoFilters) {
            JsonObject typeFilter = new JsonObject();
            typeFilter.add("featurepath", new JsonPrimitive(description.getFeaturePath()));
            typeFilter.add("type", new JsonPrimitive(description.getFilterType()));
            typeFilter.add("filter", new JsonPrimitive(description.getFilter()));
            filterArray.add(typeFilter);
        }
        message.add("filter", filterArray);
        return message;
    }

    /**
     * Gets the remote ref that matches the provided ref spec.
     * 
     * @param refspec the refspec to parse
     * @return the matching {@link Ref} or {@link Optional#absent()} if the ref could not be found
     */
    @Override
    protected Optional<Ref> getRemoteRef(String refspec) {
        return HttpUtils.getRemoteRef(repositoryURL, refspec);
    }

    /**
     * Perform pre-push actions.
     */
    @Override
    protected void beginPush() {
        HttpUtils.beginPush(repositoryURL);
    }

    /**
     * Perform post-push actions, this includes verification that the remote wasn't changed while we
     * were pushing.
     * 
     * @param refspec the refspec that we are pushing to
     * @param newCommitId the new commit id
     * @param originalRefValue the original value of the ref before pushing
     */
    @Override
    protected void endPush(String refspec, ObjectId newCommitId, String originalRefValue) {
        HttpUtils.endPush(repositoryURL, refspec, newCommitId, originalRefValue);
    }

    @Override
    protected void pushSparseCommit(final Repository local, ObjectId commitId) {
        Repository from = local;
        Optional<RevObject> object = from.command(RevObjectParse.class).setObjectId(commitId)
                .call();
        if (object.isPresent() && object.get().getType().equals(TYPE.COMMIT)) {
            RevCommit commit = (RevCommit) object.get();
            ObjectId parent = ObjectId.NULL;
            List<ObjectId> newParents = new LinkedList<ObjectId>();
            for (int i = 0; i < commit.getParentIds().size(); i++) {
                ObjectId parentId = commit.getParentIds().get(i);
                if (i != 0) {
                    Optional<ObjectId> commonAncestor = from.command(FindCommonAncestor.class)
                            .setLeftId(commit.getParentIds().get(0)).setRightId(parentId).call();
                    if (commonAncestor.isPresent()) {
                        if (from.command(CheckSparsePath.class).setStart(parentId)
                                .setEnd(commonAncestor.get()).call()) {
                            // This should be the base commit to preserve changes that were filtered
                            // out.
                            newParents.add(0, from.graphDatabase().getMapping(parentId));
                            continue;
                        }
                    }
                }
                newParents.add(from.graphDatabase().getMapping(parentId));
            }
            if (newParents.size() > 0) {
                parent = from.graphDatabase().getMapping(newParents.get(0));
            }
            try (AutoCloseableIterator<DiffEntry> diffIter = from.command(DiffOp.class)
                    .setNewVersion(commitId).setOldVersion(parent).setReportTrees(true).call()) {

                // connect and send packed changes
                final URL resourceURL;
                try {
                    resourceURL = new URL(repositoryURL.toString() + "/repo/applychanges");
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }

                final HttpURLConnection connection;
                final OutputStream out;
                try {
                    connection = (HttpURLConnection) resourceURL.openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    out = connection.getOutputStream();
                    // pack the commit object
                    final RevObjectSerializer writer = DataStreamRevObjectSerializerV1.INSTANCE;
                    writer.write(commit, out);

                    // write the new parents
                    out.write(newParents.size());
                    for (ObjectId parentId : newParents) {
                        out.write(parentId.getRawValue());
                    }

                    // pack the changes
                    BinaryPackedChanges changes = new BinaryPackedChanges(from);
                    changes.write(out, diffIter);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                final InputStream in;
                try {
                    in = connection.getInputStream();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(in));

                    String line = rd.readLine();
                    if (line != null) {
                        ObjectId remoteCommitId = ObjectId.valueOf(line);
                        from.graphDatabase().map(commit.getId(), remoteCommitId);
                        from.graphDatabase().map(remoteCommitId, commit.getId());
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    /**
     * Retrieves an object with the specified id from the remote.
     * 
     * @param objectId the object to get
     * @return the fetched object
     */
    @Override
    protected Optional<RevObject> getObject(ObjectId objectId) {
        return HttpUtils.getNetworkObject(repositoryURL, null, objectId);
    }

    /**
     * Updates the remote ref that matches the given refspec.
     * 
     * @param refspec the ref to update
     * @param commitId the new value of the ref
     * @param delete if true, the remote ref will be deleted
     * @return the updated ref
     */
    @Override
    protected Optional<Ref> updateRemoteRef(String refspec, ObjectId commitId, boolean delete) {
        return HttpUtils.updateRemoteRef(repositoryURL, refspec, commitId, delete);
    }

    /**
     * Gets the depth of the remote repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getDepth() {
        return HttpUtils.getDepth(repositoryURL, null);
    }

}
