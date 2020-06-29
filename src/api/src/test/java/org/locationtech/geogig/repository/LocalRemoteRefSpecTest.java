package org.locationtech.geogig.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class LocalRemoteRefSpecTest {

    private String remoteName = "upstream";

    private List<LocalRemoteRefSpec> parse(String specs) {
        return LocalRemoteRefSpec.parse(remoteName, specs);
    }

    private LocalRemoteRefSpec parseSingle(String specs) {
        List<LocalRemoteRefSpec> parsed = parse(specs);
        assertEquals(1, parsed.size());
        return parsed.get(0);
    }

    public @Test void testNullSpec() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse(null));
        assertThat(e.getMessage(), containsString("no refspecs provided"));
    }

    public @Test void testEmptySpec() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(" "));
        assertThat(e.getMessage(), containsString("no refspecs provided"));
    }

    public @Test void testDefaultSpecs() {
        LocalRemoteRefSpec spec = parseSingle("+refs/heads/*:refs/remotes/upstream/*");
        assertTrue(spec.isAllChildren());
        assertTrue(spec.isForce());
        assertEquals("refs/heads", spec.getRemote());
        assertEquals("refs/remotes/upstream", spec.getLocal());

        assertEquals("refs/remotes/upstream/master", spec.mapToLocal("refs/heads/master").get());
    }

    public @Test void testDefaultSpecsNoLocalTargetSpecified() {
        LocalRemoteRefSpec spec = parseSingle("+refs/heads/*");
        assertTrue(spec.isAllChildren());
        assertTrue(spec.isForce());
        assertEquals("refs/heads", spec.getRemote());
        assertEquals(String.format("refs/remotes/%s", remoteName), spec.getLocal());

        assertEquals(String.format("refs/remotes/%s/master", remoteName),
                spec.mapToLocal("refs/heads/master").get());
        assertFalse(spec.mapToLocal("HEAD").isPresent());
        assertFalse(spec.mapToLocal("refs/tags/tag1").isPresent());
    }

    public @Test void testSingle() {
        LocalRemoteRefSpec spec = parseSingle("+refs/heads/abranch:refs/heads/pr/1");
        assertFalse(spec.isAllChildren());
        assertTrue(spec.isForce());
        assertEquals("refs/heads/abranch", spec.getRemote());
        assertEquals("refs/heads/pr/1", spec.getLocal());

        assertEquals("refs/heads/pr/1", spec.mapToLocal("refs/heads/abranch").get());
        assertFalse(spec.mapToLocal("refs/heads/master").isPresent());
    }

    public @Test void testSingleNoTargetSpecified() {
        LocalRemoteRefSpec spec = parseSingle("refs/heads/abranch:");
        assertFalse(spec.isAllChildren());
        assertFalse(spec.isForce());
        assertEquals("refs/heads/abranch", spec.getRemote());
        assertEquals(String.format("refs/remotes/%s/abranch", remoteName), spec.getLocal());

        assertEquals(spec.getLocal(), spec.mapToLocal("refs/heads/abranch").get());
        assertFalse(spec.mapToLocal("refs/heads/master").isPresent());
    }
}
