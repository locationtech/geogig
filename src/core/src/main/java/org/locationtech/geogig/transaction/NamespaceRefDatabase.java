package org.locationtech.geogig.transaction;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingRefDatabase;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;

import lombok.NonNull;

class NamespaceRefDatabase extends ForwardingRefDatabase implements RefDatabase {

    private final String namespace;

    public NamespaceRefDatabase(@NonNull RefDatabase actual, @NonNull String namespace) {
        super(actual);
        this.namespace = namespace;
    }

    public @Override void close() {
        super.deleteAll(namespace);
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        return super.get(toInternal(name)).map(this::toExternal);
    }

    public @Override List<Ref> getAllPresent(@NonNull Iterable<String> names) {
        return toExternal(super.getAllPresent(Iterables.transform(names, this::toInternal)));
    }

    public @Override @NonNull List<Ref> getAll() {
        Predicate<Ref> noInternalTransactions = (ref -> !Ref.isChild(Ref.TRANSACTIONS_PREFIX,
                ref.getName()));
        return toExternal(super.getAll(namespace)).stream().filter(noInternalTransactions)
                .collect(Collectors.toList());
    }

    public @Override @NonNull List<Ref> getAll(@NonNull String prefix) {
        return toExternal(super.getAll(toInternal(prefix)));
    }

    public @Override RefChange put(@NonNull Ref ref) {
        return toExternal(super.put(toInternal(ref)));
    }

    public @Override @NonNull RefChange putRef(@NonNull String name, @NonNull ObjectId value) {
        return toExternal(super.putRef(toInternal(name), value));
    }

    public @Override @NonNull RefChange putSymRef(@NonNull String name, @NonNull String target) {
        String internalName = toInternal(name);
        String internalTarget = toInternal(target);
        return toExternal(super.putSymRef(internalName, internalTarget));
    }

    public @Override @NonNull List<RefChange> putAll(@NonNull Iterable<Ref> refs) {
        List<@NonNull Ref> internal = Streams.stream(refs).map(this::toInternal)
                .collect(Collectors.toList());
        return super.putAll(internal).stream().map(this::toExternal).collect(Collectors.toList());
    }

    public @Override @NonNull RefChange delete(@NonNull String refName) {
        return toExternal(super.delete(toInternal(refName)));
    }

    public @Override List<RefChange> delete(@NonNull Iterable<String> refNames) {
        return super.delete(Iterables.transform(refNames, this::toInternal)).stream()
                .map(this::toExternal).collect(Collectors.toList());
    }

    public @Override @NonNull RefChange delete(@NonNull Ref ref) {
        return toExternal(super.delete(toInternal(ref)));
    }

    public @Override @NonNull List<Ref> deleteAll() {
        return toExternal(super.deleteAll(toInternal("")));
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        return toExternal(super.deleteAll(toInternal(namespace)));
    }

    private List<Ref> toExternal(List<Ref> prefixed) {
        return prefixed.stream().map(this::toExternal).collect(Collectors.toList());
    }

    private Ref toExternal(Ref prefixed) {
        String internalName = prefixed.getName();
        String externalName = toExternal(internalName);
        if (prefixed instanceof SymRef) {
            return new SymRef(externalName, toExternal(prefixed.peel()));
        }
        return new Ref(externalName, prefixed.getObjectId());
    }

    private String toExternal(String internalName) {
        String externalName = Ref.child(namespace, internalName);
        return externalName;
    }

    private RefChange toExternal(RefChange prefixed) {
        return RefChange.of(toExternal(prefixed.name()), prefixed.oldValue().map(this::toExternal),
                prefixed.newValue().map(this::toExternal));
    }

    public @NonNull Ref toInternal(@NonNull Ref ref) {
        String internalName = toInternal(ref.getName());
        if (ref instanceof SymRef) {
            return new SymRef(internalName, toInternal(ref.peel()));
        }
        return new Ref(internalName, ref.getObjectId());
    }

    private @NonNull String toInternal(@NonNull String name) {
        return Ref.append(namespace, name);
    }

}
