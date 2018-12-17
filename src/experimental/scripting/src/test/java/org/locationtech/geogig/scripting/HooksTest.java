/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.scripting;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.net.URI;
import java.util.ServiceLoader;

import org.junit.Test;
import org.locationtech.geogig.hooks.CannotRunGeogigOperationException;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class HooksTest extends RepositoryTestCase {

    private File resolveHooksFolder() {
        URI repoURI = ResolveGeogigURI.lookup(geogig.getPlatform().pwd()).orNull();
        File hooksFolder = new File(new File(repoURI), "hooks");
        assertTrue(hooksFolder.exists());
        assertTrue(hooksFolder.isDirectory());
        return hooksFolder;
    }
    @Override
    protected void setUpInternal() throws Exception {
        File hooksFolder = resolveHooksFolder();
        File[] files = hooksFolder.listFiles();
        for (File file : files) {
            file.delete();
            assertFalse(file.exists());
        }
    }

    @Test
    public void testHookWithError() throws Exception {
        CharSequence wrongHookCode = "this is a syntactically wrong sentence";
        File hooksFolder = resolveHooksFolder();
        File commitPreHookFile = new File(hooksFolder, "pre_commit.js");

        Files.asCharSink(commitPreHookFile, Charsets.UTF_8).write(wrongHookCode);

        insertAndAdd(points1);
        try {
            geogig.command(CommitOp.class).setMessage("A message").call();
            fail();
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testHook() throws Exception {
        // a hook that only accepts commit messages longer with at least 4 words, and converts
        // message to lower case
        CharSequence commitPreHookCode = "exception = Packages.org.locationtech.geogig.hooks.CannotRunGeogigOperationException;\n"
                + "msg = params.get(\"message\");\n" + "if (msg.length() < 30){\n"
                + "\tthrow new exception(\"Commit messages must have at least 30 characters\");\n}"
                + "params.put(\"message\", msg.toLowerCase());";

        File hooksFolder = resolveHooksFolder();
        File commitPreHookFile = new File(hooksFolder, "pre_commit.js");

        Files.asCharSink(commitPreHookFile, Charsets.UTF_8).write(commitPreHookCode);

        insertAndAdd(points1);
        try {
            geogig.command(CommitOp.class).setMessage("A short message").call();
            fail();
        } catch (Exception e) {
            String javaVersion = System.getProperty("java.version");
            // Rhino in jdk6 throws a different exception
            if (javaVersion.startsWith("1.6")) {
                String expected = "Script " + commitPreHookFile + " threw an exception";
                assertTrue(e.getMessage(), e.getMessage().contains(expected));
            } else {
                assertTrue(e.getMessage(), e.getMessage()
                        .startsWith("Commit messages must have at least 30 characters"));
            }
        }

        String longMessage = "THIS IS A LONG UPPERCASE COMMIT MESSAGE";
        RevCommit commit = geogig.command(CommitOp.class).setMessage(longMessage).call();
        assertEquals(longMessage.toLowerCase(), commit.getMessage());

    }

    @Test
    public void testExecutableScriptFileHook() throws Exception {
        File hooksFolder = resolveHooksFolder();
        File commitPreHookFile;
        String commitPreHookCode;
        // a hook that returns non-zero
        if (Scripting.isWindows()) {
            commitPreHookCode = "exit 1";
        } else {
            commitPreHookCode = "#!/bin/sh\nexit 1";
        }
        commitPreHookFile = new File(hooksFolder, "pre_commit.bat");
        Files.asCharSink(commitPreHookFile, Charsets.UTF_8).write(commitPreHookCode);
        commitPreHookFile.setExecutable(true);

        insertAndAdd(points1);
        try {
            geogig.command(CommitOp.class).setMessage("Message").call();
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof CannotRunGeogigOperationException);
        }

        // a hook that returns zero
        if (Scripting.isWindows()) {
            commitPreHookCode = "exit 0";
        } else {
            commitPreHookCode = "#!/bin/sh\nexit 0";
        }
        commitPreHookFile = new File(hooksFolder, "pre_commit.bat");
        Files.asCharSink(commitPreHookFile, Charsets.UTF_8).write(commitPreHookCode);
        commitPreHookFile.setExecutable(true);

        geogig.command(CommitOp.class).setMessage("Message").call();

    }

    @Test(expected=CannotRunGeogigOperationException.class)
    public void testFailingPostPostProcessHook() throws Exception {
        CharSequence postHookCode = "exception = Packages.org.locationtech.geogig.hooks.CannotRunGeogigOperationException;\n"
                + "throw new exception();";
        File hooksFolder = resolveHooksFolder();
        File commitPostHookFile = new File(hooksFolder, "post_commit.js");

        Files.asCharSink(commitPostHookFile, Charsets.UTF_8).write(postHookCode);

        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("A message").call();

    }

    @Test
    public void testClasspathHook() throws Exception {
        ClasspathHookTest.ENABLED = true;
        try {
            ClasspathHookTest.CAPTURE_CLASS = AddOp.class;
            insertAndAdd(points1);
            assertEquals(AddOp.class, ClasspathHookTest.PRE_OP.getClass());
            assertEquals(AddOp.class, ClasspathHookTest.POST_OP.getClass());
        } finally {
            ClasspathHookTest.reset();
        }
    }

    @Test
    public void testClasspathHookPreFail() throws Exception {
        ClasspathHookTest.ENABLED = true;
        try {
            ClasspathHookTest.PRE_FAIL = true;
            ClasspathHookTest.CAPTURE_CLASS = AddOp.class;
            try {
                insertAndAdd(points1);
                fail("Expected pre hook exception");
            } catch (CannotRunGeogigOperationException e) {
                assertEquals("expected", e.getMessage());
            }
            assertEquals(AddOp.class, ClasspathHookTest.PRE_OP.getClass());
            assertEquals(AddOp.class, ClasspathHookTest.PRE_OP.getClass());
        } finally {
            ClasspathHookTest.reset();
        }
    }

    @Test
    public void testClasspathHookPostFail() throws Exception {
        ClasspathHookTest.ENABLED = true;
        try {
            ClasspathHookTest.POST_FAIL = true;
            ClasspathHookTest.CAPTURE_CLASS = AddOp.class;

            insertAndAdd(points1);
            // post hook errors should not forbid the operation to return successfully
            assertTrue(ClasspathHookTest.POST_EXCEPTION_THROWN);

            assertEquals(AddOp.class, ClasspathHookTest.PRE_OP.getClass());
            assertEquals(AddOp.class, ClasspathHookTest.PRE_OP.getClass());
        } finally {
            ClasspathHookTest.reset();
        }
    }

    /**
     * This command hook is discoverable through the {@link ServiceLoader} SPI as there's a
     * {@code org.locationtech.geogig.hooks.CommandHook} file in
     * {@code src/test/resources/META-INF/services} but the static ENABLED flag must be set by the
     * test case for it to be run.
     */
    public static final class ClasspathHookTest implements CommandHook {
        private static boolean ENABLED = false;

        private static boolean PRE_FAIL = false;

        private static boolean POST_FAIL = false;

        private static boolean POST_EXCEPTION_THROWN;

        @SuppressWarnings("rawtypes")
        private static Class<? extends AbstractGeoGigOp> CAPTURE_CLASS;

        private static AbstractGeoGigOp<?> PRE_OP, POST_OP;

        public ClasspathHookTest() {
            reset();
        }

        private static void reset() {
            ENABLED = false;
            PRE_FAIL = false;
            POST_FAIL = false;
            CAPTURE_CLASS = AbstractGeoGigOp.class;
            PRE_OP = null;
            POST_OP = null;
            POST_EXCEPTION_THROWN = false;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
            boolean enabled = ENABLED;
            Class<? extends AbstractGeoGigOp> captureClass = CAPTURE_CLASS;
            checkNotNull(clazz);
            checkNotNull(captureClass);
            boolean applies = enabled && CAPTURE_CLASS.equals(clazz);
            return applies;
        }

        @Override
        public <C extends AbstractGeoGigOp<?>> C pre(C command)
                throws CannotRunGeogigOperationException {
            checkState(ENABLED);
            PRE_OP = command;
            if (PRE_FAIL) {
                throw new CannotRunGeogigOperationException("expected");
            }
            return command;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T post(AbstractGeoGigOp<T> command, Object retVal, RuntimeException exception)
                throws Exception {
            checkState(ENABLED);
            POST_OP = command;
            if (POST_FAIL) {
                POST_EXCEPTION_THROWN = true;
                throw new RuntimeException("expected");
            }
            return (T) retVal;
        }

    }

}
