package org.locationtech.geogig.remote.http.pack;

import static com.google.common.base.Preconditions.checkState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.v2_3.DataStreamRevObjectSerializerV2_3;

public class StreamingPackIO {

    final byte[] delimiter = { 'p', 'a', 'c', 'k' };

    public static enum Event {
        REF_START, //
        REF_END, //
        OBJECT_STREAM_START, //
        OBJECT, //
        OBJECT_STREAM_END //
    }

    private RevObjectSerializer objmarshaller = DataStreamRevObjectSerializerV2_3.INSTANCE;

    public @Nullable RevObject readObject(DataInputStream in) throws IOException {
        Event evt = readNextEvent(in);
        if (evt == Event.OBJECT) {
            ObjectId id = readId(in);
            RevObject object = objmarshaller.read(id, in);
            return object;
        }
        checkState(evt == Event.OBJECT_STREAM_END, "expected OBJECT_STREAM_END got %s", evt);
        return null;
    }

    public void writeObject(RevObject o, DataOutputStream out) throws IOException {
        out.writeByte(Event.OBJECT.ordinal());
        writeId(o.getId(), out);
        objmarshaller.write(o, out);
    }

    public void readHeader(DataInputStream in) throws IOException {
        require(in, delimiter);
    }

    public void writeHeader(DataOutputStream out) throws IOException {
        out.write(delimiter);
    }

    private void require(DataInputStream in, byte[] bytes) throws IOException {
        byte[] buff = new byte[bytes.length];
        in.readFully(buff);
        boolean equals = Arrays.equals(bytes, buff);
        if (!equals) {
            throw new IllegalStateException(String.format("expected %s, got %s",
                    Arrays.toString(bytes), Arrays.toString(buff)));
        }
    }

    public Event readNextEvent(DataInputStream in) throws IOException {
        int event = in.readByte() & 0xFF;
        Event evt = Event.values()[event];
        return evt;
    }

    public @Nullable ObjectId readId(DataInputStream in) throws IOException {
        ObjectId id = ObjectId.readFrom(in);
        return id.isNull() ? null : id;
    }

    public void writeId(ObjectId id, DataOutputStream out) throws IOException {
        id.writeTo(out);
    }

}
