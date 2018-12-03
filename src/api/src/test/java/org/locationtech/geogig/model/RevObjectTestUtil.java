package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

public @UtilityClass class RevObjectTestUtil {

    public static void deepEquals(@NonNull RevCommit expected, @NonNull RevCommit actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCommitter(), actual.getCommitter());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getMessage(), actual.getMessage());
        assertEquals(expected.getParentIds(), actual.getParentIds());
        assertEquals(expected.getTreeId(), actual.getTreeId());
        assertEquals(expected.getType(), actual.getType());
    }

}
