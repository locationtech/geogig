package org.locationtech.geogig.storage.postgresql.v9;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.TableNames;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class PGRefDatabaseWorker {

    private final @NonNull Environment env;

    private static final String UPSERT_RETURNING_OLD_VALUE = "INSERT INTO %s (repository, path, name, value)\n"
            + "VALUES (?, ?, ?, ?)\n" //
            + "ON CONFLICT(repository, path, name) DO\n" //
            + "UPDATE SET value = ?\n"//
            + "RETURNING value AS new_value, (SELECT value FROM %s WHERE repository = ? AND path = ? and NAME = ?) AS old_value;";

    public @NonNull RefChange put(@NonNull Connection conn, @NonNull Ref ref) throws SQLException {
        // if (ref instanceof SymRef && !get(conn, ref.peel().getName()).isPresent()) {
        // throw new IllegalArgumentException("Target ref does not exist: " + ref);
        // }

        final int repoId = env.getRepositoryId();
        final String parentPath = asStoredPrefix(Ref.parentPath(ref.getName()));
        final String simpleName = Ref.simpleName(ref.getName());
        final String value = asValue(ref);

        final TableNames tables = env.getTables();
        final String table = tables.refs();

        final String upsert = String.format(UPSERT_RETURNING_OLD_VALUE, table, table);
        String oldValue;
        try (PreparedStatement pst = conn.prepareStatement(upsert)) {
            // insert values...
            pst.setInt(1, repoId);
            pst.setString(2, parentPath);
            pst.setString(3, simpleName);
            pst.setString(4, value);
            // update where value...
            pst.setString(5, value);
            // update where clause...
            pst.setInt(6, repoId);
            pst.setString(7, parentPath);
            pst.setString(8, simpleName);
            try (ResultSet result = pst.executeQuery()) {
                Preconditions.checkState(result.next());
                oldValue = result.getString("old_value");
            }
        }

        Ref old;
        if (null == oldValue) {
            old = null;
        } else if (isSymRefValue(oldValue)) {
            String targetName = stripSymPrefix(oldValue);
            Ref targetRef = get(conn, targetName)
                    .orElseGet(() -> new Ref(targetName, ObjectId.NULL));
            old = new SymRef(ref.getName(), targetRef);
        } else {
            ObjectId id = ObjectId.valueOf(oldValue);
            old = new Ref(ref.getName(), id);
        }
        return RefChange.of(ref.getName(), old, ref);
    }

    public List<RefChange> putAll(@NonNull Connection conn, @NonNull List<Ref> refs)
            throws SQLException {

        List<RefChange> symrefs = putAllInternal(conn,
                refs.stream().filter(r -> (r instanceof SymRef)).collect(Collectors.toList()));

        List<RefChange> result = putAllInternal(conn,
                refs.stream().filter(r -> !(r instanceof SymRef)).collect(Collectors.toList()));

        if (!symrefs.isEmpty()) {
            // make sure symref's new value matches the argument ref when the target ref is also
            // being inserted
            Map<String, Ref> byName = refs.stream().collect(Collectors.toMap(Ref::getName, r -> r));
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

    private List<RefChange> putAllInternal(@NonNull Connection conn, List<Ref> refs)
            throws SQLException {
        List<RefChange> updated = new ArrayList<>(refs.size());
        for (Ref ref : refs) {
            updated.add(put(conn, ref));
        }
        return updated;
    }

    public Optional<Ref> get(@NonNull Connection conn, @NonNull String name) throws SQLException {
        List<Ref> matches = getPresent(conn, Collections.singletonList(name));
        return Optional.ofNullable(matches.isEmpty() ? null : matches.get(0));
    }

    public List<Ref> getPresent(@NonNull Connection conn, @NonNull Iterable<String> names)
            throws SQLException {

        final String query = String.format(//
                "SELECT (path||name) as name, value FROM %s "//
                        + "WHERE repository = ? AND (path||name) = ANY(?)", //
                env.getTables().refs());

        final List<String> storedNames = Streams.stream(names).map(this::refNameToStoredPath)
                .collect(Collectors.toList());
        final Array composedNames = toJDBCArray(conn, storedNames);
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, env.getRepositoryId());
            ps.setArray(2, composedNames);
            try (ResultSet rs = ps.executeQuery()) {
                return getFromResultset(conn, rs);
            }
        }
    }

    private List<Ref> getFromResultset(Connection conn, ResultSet rs) throws SQLException {
        Map<String, String> nameToValue = Collections.emptyMap();
        while (rs.next()) {
            if (nameToValue.isEmpty()) {
                nameToValue = new HashMap<>();
            }
            String storedName = rs.getString("name");
            String name = storedPathToRefName(storedName);
            String value = rs.getString(2);
            nameToValue.put(name, value);
        }
        return resolve(conn, nameToValue);
    }

    private List<Ref> resolve(@NonNull Connection conn, @NonNull Map<String, String> nameToValue) {
        if (nameToValue.isEmpty()) {
            return Collections.emptyList();
        }
        List<Ref> refs = new ArrayList<>(nameToValue.size());
        nameToValue.forEach((name, value) -> refs.add(resolve(conn, name, value, nameToValue)));
        return refs;
    }

    private Ref resolve(@NonNull Connection conn, @NonNull String name, @NonNull String value,
            @NonNull Map<String, String> nameToValue) {
        Ref ref;
        if (isSymRefValue(value)) {
            String targetName = stripSymPrefix(value);
            String targetValue = nameToValue.get(targetName);
            Ref target;
            if (targetValue == null) {
                try {
                    target = get(conn, targetName)
                            .orElseGet(() -> new Ref(targetName, ObjectId.NULL));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                target = resolve(conn, targetName, targetValue, nameToValue);
            }
            ref = new SymRef(name, target);
        } else {
            ref = new Ref(name, ObjectId.valueOf(value));
        }
        return ref;
    }

    private String storedPathToRefName(@NonNull String name) {
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private String refNameToStoredPath(@NonNull String name) {
        return name.indexOf('/') > -1 ? name : ("/" + name);
    }

    private boolean isSymRefValue(@NonNull String value) {
        return value.startsWith("ref: ");
    }

    private Array toJDBCArray(Connection cx, final List<String> values) throws SQLException {
        Object[] arr = new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i);
        }
        return cx.createArrayOf("text", arr);
    }

    private String stripSymPrefix(String symRefValue) {
        return symRefValue.substring("ref: ".length());
    }

    private String asValue(Ref ref) {
        return ref instanceof SymRef ? ("ref: " + ref.peel().getName())
                : ref.getObjectId().toString();
    }

    private String asStoredPrefix(@NonNull String prefix) {
        return prefix.endsWith("/") ? prefix : (prefix + "/");
    }

    public List<Ref> getByPrefix(Connection conn, List<@NonNull String> prefixes)
            throws SQLException {
        if (prefixes.isEmpty()) {
            return Collections.emptyList();
        }

        final String oredPaths = prefixes.stream()//
                .map(this::asStoredPrefix)//
                .map(path -> String.format("path LIKE '%s%%'", path))
                .collect(Collectors.joining(" OR "));

        final String query = String.format(//
                "SELECT (path||name) AS name, value FROM %s"//
                        + " WHERE repository = ? AND (%s)", //
                env.getTables().refs(), //
                oredPaths);

        try (PreparedStatement st = conn.prepareStatement(query)) {
            st.setInt(1, env.getRepositoryId());
            try (ResultSet rs = st.executeQuery()) {
                return getFromResultset(conn, rs);
            }
        }
    }

    public List<RefChange> deleteByName(@NonNull Connection conn, @NonNull Iterable<String> names)
            throws SQLException {

        final List<String> storedNames = Streams.stream(names).map(this::refNameToStoredPath)
                .collect(Collectors.toList());

        final String query = String.format(
                "DELETE FROM %s WHERE repository = ? AND (path||name) = ANY(?) RETURNING (path||name) AS name, value", //
                env.getTables().refs());

        final Array composedNames = toJDBCArray(conn, storedNames);
        List<Ref> deleted;
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, env.getRepositoryId());
            ps.setArray(2, composedNames);
            try (ResultSet rs = ps.executeQuery()) {
                deleted = getFromResultset(conn, rs);
            }
        }
        // gotta return a ChangeRef for the names that didn't match a db record too...
        Map<String, Ref> deletedByName = deleted.stream()
                .collect(Collectors.toMap(Ref::getName, r -> r));

        return Streams.stream(names).map(name -> RefChange.of(name, deletedByName.get(name), null))
                .collect(Collectors.toList());
    }

    public List<Ref> deleteByPrefix(Connection conn, List<String> prefixes) throws SQLException {
        if (prefixes.isEmpty()) {
            return Collections.emptyList();
        }

        final String oredPaths = prefixes.stream()
                .map(path -> String.format("path LIKE '%s/%%'", path))
                .collect(Collectors.joining(" OR "));

        final String query = String.format(//
                "DELETE FROM %s WHERE repository = ? AND (%s)"//
                        + " RETURNING (path||name) AS name, value", //
                env.getTables().refs(), //
                oredPaths);

        try (PreparedStatement st = conn.prepareStatement(query)) {
            st.setInt(1, env.getRepositoryId());
            try (ResultSet rs = st.executeQuery()) {
                return getFromResultset(conn, rs);
            }
        }
    }

}
