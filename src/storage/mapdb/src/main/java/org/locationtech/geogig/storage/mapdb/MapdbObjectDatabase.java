/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.fs.FileBlobStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * An Object database that uses a MapDB file database for persistence.
 * 
 * @see http://mapdb.org/
 */
public class MapdbObjectDatabase implements ObjectDatabase {

	protected final ConfigDatabase config;

	protected DB db = null;

	// TODO could be changed to Map <ObjectId, byte[]> as ObjectId implements serializable
	// what about prefix finding then?
	protected ConcurrentNavigableMap<String, byte[]> collection = null;

	protected ObjectSerializingFactory serializers = DataStreamSerializationFactoryV1.INSTANCE;

	private final String collectionName = "objects";

	private ExecutorService executor;

	private MapdbConflictsDatabase conflicts;

	private FileBlobStore blobStore;

	private Platform platform;

	@Inject
	public MapdbObjectDatabase(ConfigDatabase config, ExecutorService executor,
			Platform platform) {
		this.config = config;
		this.executor = executor;
		this.platform = platform;
	}

	/*
	 * Things to know about MapDB Transactions (write-ahead-log) can be disabled
	 * with DBMaker.transactionDisable(), this will MapDB much faster. However,
	 * without WAL the store gets corrupted when not closed correctly.
	 * 
	 * Keys and values must be immutable. MapDB may serialize them on background
	 * thread, put them into instance cache... Modifying an object after it was
	 * stored is a bad idea.
	 * 
	 * MapDB is much faster with memory mapped files. But those cause problems
	 * on 32bit JVMs and are disabled by default. Use
	 * DBMaker.fileMmapEnableIfSupported() to enable them on 32bit systems.
	 * 
	 * 
	 * There is instance cache which uses more memory, but makes MapDB faster.
	 * Use DBMaker.cacheHashTableEnable()
	 * 
	 * MapDB does not run compaction on the background. You need to call
	 * DB.compact() from time to time.
	 * 
	 * MapDB file storage can be only opened by one user at time. File lock
	 * should prevent file being used multiple times. But if file lock fails to
	 * prevent it, the file will become corrupted when opened (and written into)
	 * by multiple users.
	 */

	@Override
	public synchronized void open() {
		if (db != null) {
			return;
		}
		Optional<URI> repoPath = new ResolveGeogigURI(platform, null).call();
		Preconditions.checkState(repoPath.isPresent(),
				"Not inside a geogig directory");
		URI uri = repoPath.get();
		Preconditions.checkState("file".equals(uri.getScheme()),
				"Repository URL is not file system based: %s", uri);
		File repoLocation = new File(uri);
		File storeDirectory = new File(repoLocation, "objects");

		if (!storeDirectory.exists() && !storeDirectory.mkdirs()) {
			throw new IllegalStateException(
					"Unable to create Environment directory: '"
							+ storeDirectory.getAbsolutePath() + "'");
		}

		db = DBMaker.fileDB(new File(storeDirectory, "objectdb.mapdb"))
				.closeOnJvmShutdown().fileMmapEnableIfSupported()
				.cacheHashTableEnable().make();

		collection = db.treeMap(collectionName);

		conflicts = new MapdbConflictsDatabase(db);
		blobStore = new FileBlobStore(platform);
	}

	@Override
	public synchronized boolean isOpen() {
		return db != null;
	}

	@Override
	public void configure() throws RepositoryConnectionException {
		RepositoryConnectionException.StorageType.OBJECT.configure(config,
				"mapdb", "0.1");
	}

	@Override
	public void checkConfig() throws RepositoryConnectionException {
		RepositoryConnectionException.StorageType.OBJECT.verify(config,
				"mapdb", "0.1");
	}

	@Override
	public synchronized void close() {
		if (db != null) {
			db.commit();
			db.close();
		}
		db = null;
		collection = null;
		conflicts.close();
		conflicts = null;
		blobStore.close();
		blobStore = null;
	}

	@Override
	public ConflictsDatabase getConflictsDatabase() {
		return conflicts;
	}

	@Override
	public BlobStore getBlobStore() {
		return blobStore;
	}

	@Override
	public boolean exists(ObjectId id) {
		return collection.containsKey(id.toString());
	}

	@Override
	public List<ObjectId> lookUp(final String partialId) {

		if (partialId.matches("[a-fA-F0-9]+") && partialId.length() > 0) {
			 char nextLetter = (char) (partialId.charAt(partialId.length() -
			 1) + 1);
			 String end = partialId.substring(0, partialId.length() - 1)
			 + nextLetter;
			 SortedMap<String, byte[]> matchingPairs = collection.subMap(
			 partialId, end);
			 List<ObjectId> ids = new ArrayList<ObjectId>(4);
			 for (String objectId : matchingPairs.keySet()) {
			 ids.add(ObjectId.valueOf(objectId));
			 }
			 return ids;
		} else {
			throw new IllegalArgumentException(
					"Prefix query must be done with hexadecimal values only");
		}
	}

	@Override
	public RevObject get(ObjectId id) {
		RevObject result = getIfPresent(id);
		if (result != null) {
			return result;
		} else {
			throw new NoSuchElementException("No object with id: " + id);
		}
	}

	@Override
	public <T extends RevObject> T get(ObjectId id, Class<T> clazz) {
		return clazz.cast(get(id));
	}

	@Override
	public RevObject getIfPresent(ObjectId id) {
		String key = id.toString();
		if (collection.containsKey(key)) {
			return fromBytes(id, collection.get(key));
		}
		return null;
	}

	@Override
	public <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz) {
		return clazz.cast(getIfPresent(id));
	}

	@Override
	public RevTree getTree(ObjectId id) {
		return get(id, RevTree.class);
	}

	@Override
	public RevFeature getFeature(ObjectId id) {
		return get(id, RevFeature.class);
	}

	@Override
	public RevFeatureType getFeatureType(ObjectId id) {
		return get(id, RevFeatureType.class);
	}

	@Override
	public RevCommit getCommit(ObjectId id) {
		return get(id, RevCommit.class);
	}

	@Override
	public RevTag getTag(ObjectId id) {
		return get(id, RevTag.class);
	}

	private long deleteChunk(List<ObjectId> ids) {
		// TODO this might not be the most efficient way to do this.
		long deleteCounter = 0;
		for (ObjectId id : ids) {
			boolean deleted = delete(id);
			if (deleted) {
				deleteCounter++;
			}
		}
		return deleteCounter;
	}

	@Override
	public boolean delete(ObjectId id) {
		String key = id.toString();
		if (collection.containsKey(key)) {
			collection.remove(key);
			return true;
		}
		return false;
	}

	@Override
	public long deleteAll(Iterator<ObjectId> ids) {
		return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
	}

	@Override
	public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
		Iterator<List<ObjectId>> chunks = Iterators.partition(ids, 500);
		long count = 0;
		while (chunks.hasNext()) {
			count += deleteChunk(chunks.next());
		}
		return count;
	}

	@Override
	public boolean put(final RevObject object) {
		String key = object.getId().toString();
		byte[] record = toBytes(object);
		collection.put(key, record);
		db.commit();
		return true;
		// TODO In which cases do we return false here?
	}

	@Override
	public void putAll(final Iterator<? extends RevObject> objects) {
		putAll(objects, BulkOpListener.NOOP_LISTENER);
	}

	@Override
	public void putAll(Iterator<? extends RevObject> objects,
			BulkOpListener listener) {
		Preconditions.checkNotNull(executor, "executor service not set");
		if (!objects.hasNext()) {
			return;
		}

		final int bulkSize = 1000;
		final int maxRunningTasks = 10;

		final AtomicBoolean cancelCondition = new AtomicBoolean();

		List<ObjectId> ids = Lists.newArrayListWithCapacity(bulkSize);
		List<byte[]> values = Lists.newArrayListWithCapacity(bulkSize);
		List<Future<?>> runningTasks = new ArrayList<Future<?>>(maxRunningTasks);

		try {
			while (objects.hasNext()) {
				RevObject object = objects.next();
				ids.add(object.getId());
				values.add(toBytes(object));

				if (ids.size() == bulkSize || !objects.hasNext()) {
					InsertTask task = new InsertTask(collection, listener, ids,
							values, cancelCondition);
					runningTasks.add(executor.submit(task));

					if (objects.hasNext()) {
						ids = Lists.newArrayListWithCapacity(bulkSize);
						values = Lists.newArrayListWithCapacity(bulkSize);
					}
				}
				if (runningTasks.size() == maxRunningTasks) {
					waitForTasks(runningTasks);
				}
			}
			waitForTasks(runningTasks);
			db.commit();
		} catch (RuntimeException e) {
			cancelCondition.set(true);
			throw e;
		}
	}

	private void waitForTasks(List<Future<?>> runningTasks) {
		// wait...
		for (Future<?> f : runningTasks) {
			try {
				f.get();
			} catch (Exception e) {
				throw Throwables.propagate(e);
			}
		}
		runningTasks.clear();
	}

	private static class InsertTask implements Runnable {

		private ConcurrentNavigableMap<String, byte[]> collection;

		private BulkOpListener listener;

		private List<ObjectId> ids;

		private List<byte[]> objects;

		private AtomicBoolean cancelCondition;

		// TODO Interface: could we work on RevObject directly?
		// what about toBytes() then being called from a static context?
		public InsertTask(ConcurrentNavigableMap<String, byte[]> collection,
				BulkOpListener listener, List<ObjectId> ids,
				List<byte[]> objects, AtomicBoolean cancelCondition) {
			this.collection = collection;
			this.listener = listener;
			this.ids = ids;
			this.objects = objects;
			this.cancelCondition = cancelCondition;
		}

		@Override
		public void run() {
			if (cancelCondition.get()) {
				return;
			}

			for (int i = 0; i < ids.size(); i++) {
				if (cancelCondition.get()) {
					return;
				}
				ObjectId key = ids.get(i);
				byte[] value = objects.get(i);
				boolean found = collection.containsKey(key.toString());
				collection.put(ids.get(i).toString(), objects.get(i));
				if (found) {
					listener.found(key, value.length);
				} else {
					listener.inserted(key, value.length);
				}
			}
			ids.clear();
			objects.clear();
		}

	}

	@Override
	public ObjectInserter newObjectInserter() {
		return new ObjectInserter(this);
	}

	@Override
	public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
		return getAll(ids, BulkOpListener.NOOP_LISTENER);
	}

	@Override
	public Iterator<RevObject> getAll(final Iterable<ObjectId> ids,
			final BulkOpListener listener) {

		return new AbstractIterator<RevObject>() {
			final Iterator<ObjectId> queryIds = ids.iterator();

			@Override
			protected RevObject computeNext() {
				RevObject obj = null;
				while (obj == null) {
					if (!queryIds.hasNext()) {
						return endOfData();
					}
					ObjectId id = queryIds.next();
					obj = getIfPresent(id);
					if (obj == null) {
						listener.notFound(id);
					} else {
						listener.found(obj.getId(), null);
					}
				}
				return obj == null ? endOfData() : obj;
			}
		};
	}

	private RevObject fromBytes(ObjectId id, byte[] buffer) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
		RevObject result;
		try {
			result = serializers.read(id, new LZFInputStream(byteStream));
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
		return result;
	}

	private byte[] toBytes(RevObject object) {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		LZFOutputStream cOut = new LZFOutputStream(byteStream);
		try {
			serializers.write(object, cOut);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			cOut.close();
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}
		return byteStream.toByteArray();
	}

	@Override
	public String toString() {
		return String.format("%s[db: %s, collection: %s]", getClass()
				.getSimpleName(), db == null ? "<unset>" : db, collectionName);
	}
}
