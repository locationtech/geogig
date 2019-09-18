/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static org.locationtech.geogig.model.Ref.TRANSACTIONS_PREFIX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.impl.SimpleLockingRefDatabase;

import com.google.common.collect.Streams;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the heap for the storage of
 * refs.
 */
public class HeapRefDatabase extends SimpleLockingRefDatabase {

    private final ConcurrentMap<String, Object> refs = new ConcurrentHashMap<>();

    public HeapRefDatabase() {
        this(false);
    }

    public HeapRefDatabase(boolean readOnly) {
        super(readOnly);
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        return resolve(name, refs.get(name));
    }

    public @Override RefChange put(@NonNull Ref ref) {
        Optional<Ref> old = get(ref.getName());
        Object value = ref instanceof SymRef ? ((SymRef) ref).getTarget() : ref.getObjectId();
        refs.put(ref.getName(), value);
        return RefChange.of(ref.getName(), old, Optional.of(ref));
    }

    public @Override RefChange putRef(@NonNull String name, @NonNull ObjectId value) {
        return put(new Ref(name, value));
    }

    public @Override RefChange putSymRef(@NonNull String name, @NonNull String target) {
        Ref targetRef = get(target).orElseThrow(
                () -> new IllegalArgumentException("Target ref does not exist: " + target));
        return put(new SymRef(name, targetRef));
    }

    public @Override List<RefChange> putAll(@NonNull Iterable<Ref> refs) {

        List<RefChange> symrefs = Streams.stream(refs).filter(r -> (r instanceof SymRef))
                .map(this::put).collect(Collectors.toList());

        List<RefChange> result = Streams.stream(refs).filter(r -> !(r instanceof SymRef))
                .map(this::put).collect(Collectors.toList());

        if (!symrefs.isEmpty()) {
            // make sure symref's new value matches the argument ref when the target ref is also
            // being inserted
            Map<String, Ref> byName = Streams.stream(refs)
                    .collect(Collectors.toMap(Ref::getName, r -> r));
            symrefs.stream().map(change -> {
                Ref newValue = change.newValue().get();
                String target = ((SymRef) newValue).getTarget();
                if (byName.containsKey(target)) {
                    change = RefChange.of(change.name(), change.oldValue(),
                            Optional.of(new SymRef(change.name(), byName.get(target))));
                }
                return change;
            }).forEach(result::add);
        }
        return result;
    }

    public @Override RefChange delete(@NonNull String refName) {
        Optional<Ref> old = resolve(refName, refs.remove(refName));
        return RefChange.of(refName, old, Optional.empty());
    }

    public @Override RefChange delete(@NonNull Ref ref) {
        return delete(ref.getName());
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        try {
            lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        try {
            List<Ref> matches = getAll(namespace);
            matches.forEach(this::delete);
            return matches;
        } finally {
            unlock();
        }
    }

    public @Override @NonNull List<RefChange> delete(@NonNull Iterable<String> refNames) {
        try {
            lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        try {
            return Streams.stream(refNames).map(this::delete).collect(Collectors.toList());
        } finally {
            unlock();
        }
    }

    /**
     * @return all known references under the "refs" namespace (i.e. not top level ones like HEAD,
     *         etc), key'ed by ref name
     */
    public @Override @NonNull List<Ref> getAll() {
        return getAll(new RefPrefixPredicate(TRANSACTIONS_PREFIX).negate());
    }

    public @Override List<Ref> getAllPresent(@NonNull Iterable<String> names) {
        return Streams.stream(names).map(this::get).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());
    }

    public @Override @NonNull List<Ref> deleteAll() {
        List<Ref> all = getAll();
        all.forEach(this::delete);
        return all;
    }

    public @Override @NonNull List<Ref> getAll(@NonNull String namespace) {
        return getAll(new RefPrefixPredicate(namespace));
    }

    private List<Ref> getAll(Predicate<Ref> filter) {
        List<Ref> matches = new ArrayList<>();
        refs.forEach((k, v) -> resolve(k, v).filter(filter).ifPresent(matches::add));
        return matches;
    }

    private static @RequiredArgsConstructor class RefPrefixPredicate implements Predicate<Ref> {
        private final @NonNull String prefix;

        public @Override boolean test(Ref ref) {
            return Ref.isChild(prefix, ref.getName());
        }
    }

    protected @Nullable Optional<Ref> resolve(@NonNull String name, @Nullable Object value) {
        Ref resolved = null;
        if (value instanceof String) {
            String targetName = (String) value;
            Optional<Ref> target = get(targetName);
            if (target.isPresent()) {
                resolved = new SymRef(name, target.get());
            }
        } else if (value instanceof ObjectId) {
            resolved = new Ref(name, (ObjectId) value);
        }
        return Optional.ofNullable(resolved);
    }
}
