package org.locationtech.geogig.dsl;

import java.util.Set;

import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class Conflicts {
    private final @NonNull @Getter Context context;

    public boolean hasConflicts() {
        return context.conflictsDatabase().hasConflicts(null);
    }

    public void save(Iterable<Conflict> conflicts) {
        context.command(ConflictsWriteOp.class).setConflicts(conflicts).call();
    }

    public Set<String> find(@NonNull Iterable<String> paths) {
        return context.conflictsDatabase().findConflicts(null, paths);
    }

}
