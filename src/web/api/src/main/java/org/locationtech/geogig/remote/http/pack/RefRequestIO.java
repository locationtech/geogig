package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remotes.pack.RefRequest;

import com.google.common.base.Preconditions;

public class RefRequestIO {

    public void write(DataOutputStream out, RefRequest r) throws IOException {
        String name = r.name;
        ObjectId want = r.want;
        ObjectId have = r.have.orNull();

        out.writeUTF(name);
        writeId(out, want);
        writeId(out, have);
    }

    public RefRequest read(DataInputStream in) throws IOException {
        String name = in.readUTF();
        final ObjectId want = readId(in);
        final @Nullable ObjectId have = readId(in);
        return RefRequest.create(name, want, have);
    }

    void writeId(DataOutputStream out, @Nullable ObjectId id) throws IOException {
        out.writeByte(id == null ? 0 : 1);
        if (id != null) {
            id.writeTo(out);
        }
    }

    @Nullable
    ObjectId readId(DataInputStream in) throws IOException {
        final int present = in.readByte() & 0xFF;
        Preconditions.checkArgument(present == 0 || present == 1);
        ObjectId id = null;
        if (1 == present) {
            id = ObjectId.readFrom(in);
        }
        return id;
    }
}
