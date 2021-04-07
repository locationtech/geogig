package org.locationtech.geogig.storage.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Objects;

import org.junit.Test;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.RevObjectTestUtil;

import lombok.NonNull;

public class MarshallersTest {

    public @Test void testMarshalling() {
        for (FieldType ft : FieldType.values()) {
            if (FieldType.UNKNOWN != ft) {
                testMarshalling(ft);
            }
        }
    }

    public @Test void testMarshallingStringArray() {
        testMarshalling(FieldType.STRING_ARRAY, null);
        testMarshalling(FieldType.STRING_ARRAY, new String[] {});
        testMarshalling(FieldType.STRING_ARRAY, new String[] { "" });
        testMarshalling(FieldType.STRING_ARRAY, new String[] { "a\n  \n\n\tc\"\"" });
        testMarshalling(FieldType.STRING_ARRAY, new String[] { "a\n  \n\n\tc\"\"",
                "testMarshalling(FieldType.STRING_ARRAY, new String[] {\"a\n  \n\n\tc\\\"\\\"\", \"\"})" });
    }

    private void testMarshalling(FieldType ft) {
        final Object val = RevObjectTestUtil.sampleValue(ft);
        testMarshalling(ft, val);
    }

    private void testMarshalling(final @NonNull FieldType ft, final Object val) {
        String marshalled = Marshallers.marshall(val);

        Object roundTripped = Marshallers.unmarshall(marshalled, ft);
        if (val != null && val.getClass().isArray()) {
            assertTrue(Objects.deepEquals(val, roundTripped));
        } else {
            assertEquals(marshalled, val, roundTripped);
        }
    }

}
