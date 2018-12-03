/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static org.locationtech.geogig.model.Ref.TRANSACTIONS_PREFIX;
import static org.locationtech.geogig.model.Ref.append;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.storage.RefDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

/**
 * A {@link RefDatabase} decorator for a specific {@link GeogigTransaction transaction}.
 * <p>
 * This decorator creates a transaction specific namespace under the
 * {@code transactions/<transaction id>} path, and maps all query and storage methods to that
 * namespace.
 * <p>
 * This is so that every command created through the {@link GeogigTransaction transaction} used as a
 * {@link Context}, as well as the transaction specific {@link StagingAreaImpl} and
 * {@link WorkingTree} , are given this instance of {@code RefDatabase} and can do its work without
 * ever noticing its "running inside a transaction". For the command nothing changes.
 * <p>
 * {@link TransactionRefDatabase#create() create()} shall be called before this decorator gets used
 * in order for the transaction refs namespace to be created and all original references copied in
 * there, and {@link TransactionRefDatabase#close() close()} for the transaction refs namespace to
 * be deleted.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
public class TransactionRefDatabase implements RefDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionRefDatabase.class);

    private RefDatabase refDb;

    private final String txNamespace;

    private final String txChangedNamespace;

    private final String txOrigNamespace;

    public TransactionRefDatabase(final RefDatabase refDb, final UUID transactionId) {
        this.refDb = refDb;
        this.txNamespace = buildTransactionNamespace(transactionId);
        this.txChangedNamespace = append(txNamespace, "changed");
        this.txOrigNamespace = append(txNamespace, "orig");
    }

    public static String buildTransactionNamespace(final UUID transactionId) {
        return append(TRANSACTIONS_PREFIX, transactionId.toString());
    }

    @Override
    public void lock() throws TimeoutException {
        refDb.lock();
    }

    @Override
    public void unlock() {
        refDb.unlock();
    }

    @Override
    public void create() {
        refDb.create();

        // copy HEADS
        copyIfPresent(Ref.HEAD, Ref.WORK_HEAD, Ref.STAGE_HEAD, Ref.CHERRY_PICK_HEAD, Ref.MERGE_HEAD,
                Ref.ORIG_HEAD);

        copyAll(refDb.getAll(Ref.HEADS_PREFIX));
        copyAll(refDb.getAll(Ref.REMOTES_PREFIX));
        copyAll(refDb.getAll(Ref.TAGS_PREFIX));
    }

    private void copyIfPresent(String... refNames) {
        for (String refName : refNames) {
            String origValue = readRef(refName);
            if (origValue != null) {
                String origInternal = toOrigInternal(refName);
                String changedInternal = toChangedInternal(refName);
                LOGGER.debug("copy {} as {}", refName, origInternal);
                insertRef(origInternal, origValue);
                insertRef(changedInternal, origValue);
            }
        }
    }

    private String readRef(String name) {
        String value = null;
        try {
            value = refDb.getRef(name);
        } catch (IllegalArgumentException e) {
            value = refDb.getSymRef(name);
        }
        return value;
    }

    private void copyAll(Map<String, String> origRefs) {
        for (Entry<String, String> entry : origRefs.entrySet()) {

            String origName = entry.getKey();
            String origValue = entry.getValue();

            String origInternal = toOrigInternal(origName);
            String changedInternal = toChangedInternal(origName);
            insertRef(origInternal, origValue);
            insertRef(changedInternal, origValue);
        }
    }

    private void insertRef(String name, String value) {
        if (value.contains("/")) {
            refDb.putSymRef(name, value);
        } else {
            refDb.putRef(name, value);
        }
    }

    /**
     * Releases all the references for this transaction, but does not close the original
     * {@link RefDatabase}
     */
    @Override
    public void close() {
        refDb.removeAll(this.txNamespace);
    }

    /**
     * Gets the requested ref value from {@code transactions/<tx id>/<name>}
     */
    @Override
    public String getRef(final String name) {
        String internalName;
        String value;
        if (name.startsWith("changed") || name.startsWith("orig")) {
            internalName = append(txNamespace, name);
            value = refDb.getRef(internalName);
        } else {
            internalName = toChangedInternal(name);
            value = refDb.getRef(internalName);
        }
        return value;
    }

    @Override
    public String getSymRef(final String name) {
        String internalName;
        String value;
        if (name.startsWith("changed") || name.startsWith("orig")) {
            internalName = append(txNamespace, name);
            value = refDb.getSymRef(internalName);
        } else {
            internalName = toChangedInternal(name);
            value = refDb.getSymRef(internalName);
        }
        return value;
    }

    @Override
    public void putRef(final String refName, final String refValue) {
        String internalName = toChangedInternal(refName);
        LOGGER.debug("update {} as {}", refName, internalName);
        refDb.putRef(internalName, refValue);
    }

    @Override
    public void putSymRef(final String name, final String val) {
        checkArgument(!name.startsWith("ref: "),
                "Wrong value, should not contain 'ref: ': %s -> '%s'", name, val);
        String internalName = toChangedInternal(name);
        LOGGER.debug("update {} as {}", name, internalName);
        refDb.putSymRef(internalName, val);
    }

    @Override
    public String remove(final String refName) {
        String internal = toChangedInternal(refName);
        String removedValue = refDb.remove(internal);
        return removedValue;
    }

    @Override
    public Map<String, String> getAll() {
        return getAll("");
    }

    @Override
    public Map<String, String> getAll(final String prefix) {
        Map<String, String> changed = refDb.getAll(append(this.txChangedNamespace, prefix));
        return toExternal(changed);
    }

    public static class ChangedRef {
        public final String name;

        public final @Nullable String orignalValue;

        public final @Nullable String newValue;

        ChangedRef(String name, String oldv, String newv) {
            this.name = name;
            this.orignalValue = oldv;
            this.newValue = newv;
        }

        static ChangedRef of(String name, String oldv, String newv) {
            Preconditions.checkNotNull(name);
            Preconditions.checkArgument(oldv != null || newv != null);
            Preconditions.checkArgument(!Objects.equals(oldv, newv));
            return new ChangedRef(name, oldv, newv);
        }
    }

    public List<ChangedRef> changedRefs() {
        MapDifference<String, String> difference;
        {
            Map<String, String> externalOriginals;
            Map<String, String> externalChanged;
            Map<String, String> originals = refDb.getAll(this.txOrigNamespace);
            Map<String, String> changed = refDb.getAll(this.txChangedNamespace);

            externalOriginals = toExternal(originals);
            externalChanged = toExternal(changed);
            difference = Maps.difference(externalOriginals, externalChanged);
        }

        List<ChangedRef> changes = new ArrayList<>();
        // include all new refs
        difference.entriesOnlyOnRight().forEach((k, v) -> changes.add(ChangedRef.of(k, null, v)));

        // include all changed refs, with the new values
        for (Map.Entry<String, ValueDifference<String>> e : difference.entriesDiffering()
                .entrySet()) {
            String name = e.getKey();
            ValueDifference<String> valueDifference = e.getValue();
            String oldValue = valueDifference.leftValue();
            String newValue = valueDifference.rightValue();
            changes.add(ChangedRef.of(name, oldValue, newValue));
        }
        // deleted refs
        difference.entriesOnlyOnLeft().forEach((k, v) -> changes.add(ChangedRef.of(k, v, null)));
        return changes;
    }

    /**
     * The names of the refs that either have changed from their original value or didn't exist at
     * the time this method is called
     */
    public @Deprecated ImmutableSet<String> getChangedRefs() {
        Map<String, String> externalOriginals;
        Map<String, String> externalChanged;
        {
            Map<String, String> originals = refDb.getAll(this.txOrigNamespace);
            Map<String, String> changed = refDb.getAll(this.txChangedNamespace);

            externalOriginals = toExternal(originals);
            externalChanged = toExternal(changed);
        }
        MapDifference<String, String> difference;
        difference = Maps.difference(externalOriginals, externalChanged);

        Map<String, String> changes = new HashMap<>();
        // include all new refs
        changes.putAll(difference.entriesOnlyOnRight());

        // include all changed refs, with the new values
        for (Map.Entry<String, ValueDifference<String>> e : difference.entriesDiffering()
                .entrySet()) {
            String name = e.getKey();
            ValueDifference<String> valueDifference = e.getValue();
            String newValue = valueDifference.rightValue();
            changes.put(name, newValue);
        }
        return ImmutableSet.copyOf(changes.keySet());
    }

    @Override
    public Map<String, String> removeAll(String namespace) {
        final String txMappedNamespace = toChangedInternal(namespace);
        Map<String, String> removed = refDb.removeAll(txMappedNamespace);
        Map<String, String> external = toExternal(removed);
        return external;
    }

    private Map<String, String> toExternal(final Map<String, String> transactionEntries) {

        Map<String, String> transformed = Maps.newHashMap();
        for (Entry<String, String> entry : transactionEntries.entrySet()) {

            String txName = entry.getKey();
            String txValue = entry.getValue();

            String transformedName = toExternal(txName);
            String transformedValue = toExternalValue(txValue);
            transformed.put(transformedName, transformedValue);
        }
        return ImmutableMap.copyOf(transformed);
    }

    private String toChangedInternal(String name) {
        return append(txChangedNamespace, name);
    }

    private String toExternal(String name) {
        if (name.startsWith(this.txChangedNamespace)) {
            return Ref.child(this.txChangedNamespace, name);
        } else if (name.startsWith(this.txOrigNamespace)) {
            return Ref.child(this.txOrigNamespace, name);
        }
        return name;
    }

    private String toOrigInternal(String name) {
        String origName = append(txOrigNamespace, name);
        return origName;
    }

    private String toExternalValue(String origValue) {
        String txValue = origValue;
        boolean isSymRef = origValue.startsWith("ref: ");
        if (isSymRef) {
            String val = origValue.substring("ref: ".length());
            if (val.startsWith(this.txChangedNamespace)) {
                val = val.substring(this.txChangedNamespace.length());
                if (val.length() > 0 && val.charAt(0) == '/') {
                    val = val.substring(1);
                }
            }
            txValue = "ref: " + val;
        }
        return txValue;
    }

    @Override
    public void configure() {
        // No-op
    }

    @Override
    public boolean checkConfig() {
        return true;
    }
}
