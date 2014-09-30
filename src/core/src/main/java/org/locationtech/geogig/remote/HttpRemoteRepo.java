/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.porcelain.ConfigGet;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.remote.BinaryPackedObjects.IngestResults;
import org.locationtech.geogig.remote.HttpUtils.ReportingOutputStream;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.Deduplicator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * An implementation of a remote repository that exists on a remote machine and made public via an
 * http interface.
 * 
 * @see AbstractRemoteRepo
 */
class HttpRemoteRepo extends AbstractRemoteRepo {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRemoteRepo.class);

    /** Default limit in bytes for push to split the sent objects */
    private static final int DEFAULT_PUSH_BATCH_LIMIT = 4 * 1024 * 1024;

    private URL repositoryURL;

    final private DeduplicationService deduplicationService;

    /**
     * Constructs a new {@code HttpRemoteRepo} with the given parameters.
     * 
     * @param repositoryURL the url of the remote repository
     */
    public HttpRemoteRepo(URL repositoryURL, Repository localRepository,
            DeduplicationService deduplicationService) {
        super(localRepository);
        this.deduplicationService = deduplicationService;
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
     * 
     * @throws IOException
     */
    @Override
    public void open() throws IOException {

    }

    /**
     * Currently does nothing for HTTP Remote.
     * 
     * @throws IOException
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    @Override
    public Ref headRef() {
        HttpURLConnection connection = null;
        Ref headRef = null;
        try {
            String expanded = repositoryURL.toString() + "/repo/manifest";
            connection = HttpUtils.connect(expanded);

            // Get Response
            InputStream is = HttpUtils.getResponseStream(connection);
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

        } catch (Exception e) {

            Throwables.propagate(e);

        } finally {
            HttpUtils.consumeErrStreamAndCloseConnection(connection);
        }
        return headRef;
    }

    /**
     * List the remote's {@link Ref refs}.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    @Override
    public ImmutableSet<Ref> listRefs(final boolean getHeads, final boolean getTags) {
        HttpURLConnection connection = null;
        ImmutableSet.Builder<Ref> builder = new ImmutableSet.Builder<Ref>();
        try {
            String expanded = repositoryURL.toString() + "/repo/manifest";
            connection = HttpUtils.connect(expanded);

            // Get Response
            InputStream is = HttpUtils.getResponseStream(connection);
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            try {
                while ((line = rd.readLine()) != null) {
                    if ((getHeads && line.startsWith("refs/heads"))
                            || (getTags && line.startsWith("refs/tags"))) {
                        builder.add(HttpUtils.parseRef(line));
                    }
                }
            } finally {
                rd.close();
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            HttpUtils.consumeErrStreamAndCloseConnection(connection);
        }
        return builder.build();
    }

    /**
     * Fetch all new objects from the specified {@link Ref} from the remote.
     * 
     * @param ref the remote ref that points to new commit data
     * @param fetchLimit the maximum depth to fetch
     */
    @Override
    public void fetchNewData(Ref ref, Optional<Integer> fetchLimit, ProgressListener progress) {

        CommitTraverser traverser = getFetchTraverser(fetchLimit);

        try {
            progress.setDescription("Fetching objects from " + ref.getName());
            traverser.traverse(ref.getObjectId());
            List<ObjectId> want = new LinkedList<ObjectId>();
            want.addAll(traverser.commits);
            Collections.reverse(want);
            Set<ObjectId> have = new HashSet<ObjectId>();
            have.addAll(traverser.have);
            while (!want.isEmpty()) {
                progress.setProgress(0);
                fetchMoreData(want, have, progress);
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    /**
     * Push all new objects from the specified {@link Ref} to the remote.
     * 
     * @param ref the local ref that points to new commit data
     * @param refspec the remote branch to push to
     */
    @Override
    public void pushNewData(Ref ref, String refspec, ProgressListener progress)
            throws SynchronizationException {
        Optional<Ref> remoteRef = HttpUtils.getRemoteRef(repositoryURL, refspec);
        checkPush(ref, remoteRef);
        beginPush();

        progress.setDescription("Uploading objects to " + refspec);
        progress.setProgress(0);

        CommitTraverser traverser = getPushTraverser(remoteRef);

        traverser.traverse(ref.getObjectId());

        List<ObjectId> toSend = new LinkedList<ObjectId>(traverser.commits);
        Collections.reverse(toSend);
        Set<ObjectId> have = new HashSet<ObjectId>(traverser.have);

        Deduplicator deduplicator = deduplicationService.createDeduplicator();
        try {
            sendPackedObjects(toSend, have, deduplicator, progress);
        } finally {
            deduplicator.release();
        }

        ObjectId originalRemoteRefValue = ObjectId.NULL;
        if (remoteRef.isPresent()) {
            originalRemoteRefValue = remoteRef.get().getObjectId();
        }

        String nameToSet = remoteRef.isPresent() ? remoteRef.get().getName() : Ref.HEADS_PREFIX
                + refspec;

        endPush(nameToSet, ref.getObjectId(), originalRemoteRefValue.toString());
    }

    private void sendPackedObjects(final List<ObjectId> toSend, final Set<ObjectId> roots,
            Deduplicator deduplicator, final ProgressListener progress) {
        Set<ObjectId> sent = new HashSet<ObjectId>();
        while (!toSend.isEmpty()) {
            try {
                BinaryPackedObjects.Callback callback = new BinaryPackedObjects.Callback() {
                    @Override
                    public void callback(Supplier<RevObject> supplier) {
                        RevObject object = supplier.get();
                        progress.setProgress(progress.getProgress() + 1);
                        if (object instanceof RevCommit) {
                            RevCommit commit = (RevCommit) object;
                            toSend.remove(commit.getId());
                            roots.removeAll(commit.getParentIds());
                            roots.add(commit.getId());
                        }
                    }
                };
                ObjectDatabase database = localRepository.objectDatabase();
                BinaryPackedObjects packer = new BinaryPackedObjects(database);

                ImmutableList<ObjectId> have = ImmutableList.copyOf(roots);
                final boolean traverseCommits = false;

                Stopwatch sw = Stopwatch.createStarted();
                ObjectSerializingFactory serializer = DataStreamSerializationFactoryV1.INSTANCE;
                SendObjectsConnectionFactory outFactory;
                ObjectFunnel objectFunnel;

                outFactory = new SendObjectsConnectionFactory(repositoryURL);
                int pushBytesLimit = parsePushLimit();
                objectFunnel = ObjectFunnels.newFunnel(outFactory, serializer, pushBytesLimit);
                final long writtenObjectsCount = packer.write(objectFunnel, toSend, have, sent,
                        callback, traverseCommits, deduplicator);
                objectFunnel.close();
                sw.stop();

                long compressedSize = outFactory.compressedSize;
                long uncompressedSize = outFactory.uncompressedSize;
                LOGGER.info(String.format("HttpRemoteRepo: Written %,d objects."
                        + " Time to process: %s."
                        + " Compressed size: %,d bytes. Uncompressed size: %,d bytes.",
                        writtenObjectsCount, sw, compressedSize, uncompressedSize));
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }

    private int parsePushLimit() {
        final String confKey = "push.chunk.limit";
        Optional<String> configLimit = localRepository.command(ConfigGet.class).setName(confKey)
                .call();
        int limit = DEFAULT_PUSH_BATCH_LIMIT;
        if (configLimit.isPresent()) {
            String climit = configLimit.get();
            LOGGER.debug("Setting push batch limit to {} bytes as configured by {}", climit,
                    confKey);
            try {
                int tmpLimit = Integer.parseInt(climit);
                if (tmpLimit < 1024) {
                    LOGGER.warn(
                            "Value for push batch limit '{}' is set too low ({}). "
                                    + "A minimum of 1024 bytes is needed. Using the default value of {} bytes",
                            confKey, tmpLimit, limit);
                } else {
                    limit = tmpLimit;
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid config value for {}, using the default of {} bytes", confKey,
                        limit);
            }
        } else {
            LOGGER.info("No push batch limit set through {}, using the default of {} bytes",
                    confKey, limit);
        }
        return limit;
    }

    private static class SendObjectsConnectionFactory implements Supplier<OutputStream> {
        private URL repositoryURL;

        public SendObjectsConnectionFactory(URL repositoryURL) {
            this.repositoryURL = repositoryURL;
        }

        private long compressedSize, uncompressedSize;

        @Override
        public OutputStream get() {
            String expanded = repositoryURL.toString() + "/repo/sendobject";
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(expanded)
                        .openConnection();
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                connection.setChunkedStreamingMode(4096);
                connection.setRequestProperty("content-length", "-1");
                connection.setRequestProperty("content-encoding", "gzip");
                OutputStream out = connection.getOutputStream();
                final ReportingOutputStream rout = HttpUtils.newReportingOutputStream(connection,
                        out, true);
                return new FilterOutputStream(rout) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        compressedSize += ((ReportingOutputStream) super.out).compressedSize();
                        uncompressedSize += ((ReportingOutputStream) super.out).unCompressedSize();
                    }
                };
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    };

    /**
     * Delete a {@link Ref} from the remote repository.
     * 
     * @param refspec the ref to delete
     */
    @Override
    public void deleteRef(String refspec) {
        HttpUtils.updateRemoteRef(repositoryURL, refspec, null, true);
    }

    private void beginPush() {
        HttpUtils.beginPush(repositoryURL);
    }

    private void endPush(String refspec, ObjectId newCommitId, String originalRefValue) {
        HttpUtils.endPush(repositoryURL, refspec, newCommitId, originalRefValue);
    }

    /**
     * Retrieve objects from the remote repository, and update have/want lists accordingly.
     * Specifically, any retrieved commits are removed from the want list and added to the have
     * list, and any parents of those commits are removed from the have list (it only represents the
     * most recent common commits.) Retrieved objects are added to the local repository, and the
     * want/have lists are updated in-place.
     * 
     * @param want a list of ObjectIds that need to be fetched
     * @param have a list of ObjectIds that are in common with the remote repository
     * @param progress
     */
    private void fetchMoreData(final List<ObjectId> want, final Set<ObjectId> have,
            final ProgressListener progress) {
        final JsonObject message = createFetchMessage(want, have);
        final URL resourceURL;
        try {
            resourceURL = new URL(repositoryURL.toString() + "/repo/batchobjects");
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        }

        final HttpURLConnection connection;
        try {
            final Gson gson = new Gson();
            OutputStream out;
            final Writer writer;
            connection = (HttpURLConnection) resourceURL.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.addRequestProperty("Accept-Encoding", "gzip");
            out = connection.getOutputStream();
            writer = new OutputStreamWriter(out);
            gson.toJson(message, writer);
            writer.flush();
            out.flush();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        final HttpUtils.ReportingInputStream in = HttpUtils.getResponseStream(connection);

        BinaryPackedObjects unpacker = new BinaryPackedObjects(localRepository.objectDatabase());
        BinaryPackedObjects.Callback callback = new BinaryPackedObjects.Callback() {
            @Override
            public void callback(Supplier<RevObject> supplier) {
                RevObject object = supplier.get();
                progress.setProgress(progress.getProgress() + 1);
                if (object instanceof RevCommit) {
                    RevCommit commit = (RevCommit) object;
                    want.remove(commit.getId());
                    have.removeAll(commit.getParentIds());
                    have.add(commit.getId());
                } else if (object instanceof RevTag) {
                    RevTag tag = (RevTag) object;
                    want.remove(tag.getId());
                    have.remove(tag.getCommitId());
                    have.add(tag.getId());
                }
            }
        };

        Stopwatch sw = Stopwatch.createStarted();
        IngestResults ingestResults = unpacker.ingest(in, callback);
        sw.stop();

        String msg = String
                .format("Processed %,d objects. Inserted: %,d. Existing: %,d. Time: %s. Compressed size: %,d bytes. Uncompressed size: %,d bytes.",
                        ingestResults.total(), ingestResults.getInserted(),
                        ingestResults.getExisting(), sw, in.compressedSize(), in.unCompressedSize());
        LOGGER.info(msg);
        progress.setDescription(msg);
    }

    private JsonObject createFetchMessage(List<ObjectId> want, Set<ObjectId> have) {
        JsonObject message = new JsonObject();
        JsonArray wantArray = new JsonArray();
        for (ObjectId id : want) {
            wantArray.add(new JsonPrimitive(id.toString()));
        }
        JsonArray haveArray = new JsonArray();
        for (ObjectId id : have) {
            haveArray.add(new JsonPrimitive(id.toString()));
        }
        message.add("want", wantArray);
        message.add("have", haveArray);
        return message;
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    @Override
    public RepositoryWrapper getRemoteWrapper() {
        return new HttpRepositoryWrapper(repositoryURL);
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
