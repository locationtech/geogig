/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import java.net.URI;

import javax.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.plumbing.remotes.RemoteException;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.remote.http.HttpRemoteResolver;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;

import com.google.common.base.Optional;

public class RemoteManagementTest extends AbstractWebOpTest {

    @Rule
    public TestContext remote1TestContext = new TestContext();

    @Rule
    public TestContext remote2TestContext = new TestContext();

    public URI remote1URI = null;

    public URI remote2URI = null;

    @Override
    protected String getRoute() {
        return "remote";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RemoteManagement.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    private void setupRemotes(boolean addFirst, boolean addSecond) throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        URI localURI = geogig.command(ResolveGeogigURI.class).call().get();

        Repository remote1Geogig = remote1TestContext.get().getRepository();
        remote1Geogig.command(CloneOp.class).setRemoteURI(localURI).call();
        remote1URI = remote1Geogig.command(ResolveGeogigURI.class).call().get();
        if (addFirst) {
            geogig.command(RemoteAddOp.class).setName("remote1")
                    .setURL(remote1URI.toURL().toString()).call();
        }

        Repository remote2Geogig = remote2TestContext.get().getRepository();
        remote2Geogig.command(CloneOp.class).setRemoteURI(localURI).call();
        remote2URI = remote1Geogig.command(ResolveGeogigURI.class).call().get();
        if (addSecond) {
            geogig.command(RemoteAddOp.class).setName("remote2")
                    .setURL(remote2URI.toURL().toString()).call();
        }
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("list", "true", "remove", "true", "update", "true",
                "ping", "true", "verbose", "true", "remoteName", "origin", "newName", "origin2",
                "remoteURL", "some/url", "username", "Tester", "password", "pass");

        RemoteManagement op = (RemoteManagement) buildCommand(options);
        assertTrue(op.list);
        assertTrue(op.remove);
        assertTrue(op.update);
        assertTrue(op.ping);
        assertTrue(op.verbose);
        assertEquals("origin", op.remoteName);
        assertEquals("origin2", op.newName);
        assertEquals("some/url", op.remoteURL);
        assertEquals("Tester", op.username);
        assertEquals("pass", op.password);
    }

    @Test
    public void listRemotes() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("list", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "[{\"name\":\"remote1\"},{\"name\":\"remote2\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), response.getJsonArray("Remote"), true));
    }

    @Test
    public void listRemotesVerbose() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("list", "true", "verbose", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "[{\"name\":\"remote1\", \"url\":\"" + remote1URI.toURL().toString()
                + "\"},{\"name\":\"remote2\",\"url\":\"" + remote2URI.toURL().toString() + "\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), response.getJsonArray("Remote"), true));
    }

    @Test
    public void pingRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("ping", "true", "remoteName", "remote1");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertTrue(response.getJsonObject("ping").getBoolean("success"));
    }

    @Test
    public void pingInvalidRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("ping", "true", "remoteName", "nonexistent");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertFalse(response.getJsonObject("ping").getBoolean("success"));
    }

    @Test
    public void removeRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("remove", "true", "remoteName", "remote1");
        buildCommand(options).run(testContext.get());

        Optional<Remote> remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote1").call();

        assertFalse(remote.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote1", response.getString("name"));
    }

    @Test
    public void removeNullRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("remove", "true");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void removeEmptyRemoteName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("remove", "true", "remoteName", "");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void removeNonExistentRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("remove", "true", "remoteName", "nonexistent");
        ex.expect(CommandSpecException.class);
        ex.expectMessage(RemoteException.StatusCode.REMOTE_NOT_FOUND.toString());
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testEncryptDecryptPassword() {
        String password = "password";
        String encrypted = HttpRemoteResolver.encryptPassword(password);
        assertFalse(password.equals(encrypted));
        String decrypted = HttpRemoteResolver.decryptPassword(encrypted);
        assertEquals(password, decrypted);
    }

    @Test
    public void updateRemote() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "newName",
                "remote1_new", "remoteURL", "new/url", "username", "Tester", "password", "pass");
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote1_new").call().get();

        assertEquals("remote1_new", remote.getName());
        assertEquals("new/url", remote.getFetchURL());
        assertEquals("Tester", remote.getUserName());
        assertEquals(HttpRemoteResolver.encryptPassword("pass"), remote.getPassword());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote1_new", response.getString("name"));
    }

    @Test
    public void updateRemoteNoNewName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "remoteURL",
                "new/url", "username", "Tester", "password", "pass");
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote1").call().get();

        assertEquals("remote1", remote.getName());
        assertEquals("new/url", remote.getFetchURL());
        assertEquals("Tester", remote.getUserName());
        assertEquals(HttpRemoteResolver.encryptPassword("pass"), remote.getPassword());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote1", response.getString("name"));
    }

    @Test
    public void updateRemoteEmptyNewName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "newName",
                "", "remoteURL", "new/url", "username", "Tester", "password", "pass");
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote1").call().get();

        assertEquals("remote1", remote.getName());
        assertEquals("new/url", remote.getFetchURL());
        assertEquals("Tester", remote.getUserName());
        assertEquals(HttpRemoteResolver.encryptPassword("pass"), remote.getPassword());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote1", response.getString("name"));
    }

    @Test
    public void updateRemoteSameNewName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "newName",
                "remote1", "remoteURL", "new/url", "username", "Tester", "password", "pass");
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote1").call().get();

        assertEquals("remote1", remote.getName());
        assertEquals("new/url", remote.getFetchURL());
        assertEquals("Tester", remote.getUserName());
        assertEquals(HttpRemoteResolver.encryptPassword("pass"), remote.getPassword());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote1", response.getString("name"));
    }

    @Test
    public void updateRemoteNullName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "newName", "remote1_new",
                "remoteURL", "new/url", "username", "Tester", "password", "pass");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void updateRemoteEmptyName() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "", "newName",
                "remote1_new", "remoteURL", "new/url", "username", "Tester", "password", "pass");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void updateRemoteNullURL() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "newName",
                "remote1_new", "username", "Tester", "password", "pass");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No URL was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void updateRemoteEmptyURL() throws Exception {
        setupRemotes(true, true);

        ParameterSet options = TestParams.of("update", "true", "remoteName", "remote1", "newName",
                "remote1_new", "remoteURL", "", "username", "Tester", "password", "pass");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No URL was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void addRemote() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteName", "remote2", "remoteURL",
                remote2URI.toURL().toString());
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote2").call().get();

        assertEquals("remote2", remote.getName());
        assertEquals(remote2URI.toURL().toString(), remote.getFetchURL());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("remote2", response.getString("name"));
    }

    @Test
    public void addRemoteEmptyName() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteName", "", "remoteURL",
                remote2URI.toURL().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void addRemoteNullName() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteURL",
                remote2URI.toURL().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No remote was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void addRemoteEmptyURL() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteName", "remote2", "remoteURL",
                "");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No URL was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void addRemoteNullURL() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteName", "remote2");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No URL was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void addRemoteDuplicate() throws Exception {
        setupRemotes(true, false);

        ParameterSet options = TestParams.of("add", "true", "remoteName", "remote2", "remoteURL",
                remote2URI.toURL().toString());
        buildCommand(options).run(testContext.get());

        Remote remote = testContext.get().getRepository().command(RemoteResolve.class)
                .setName("remote2").call().get();
        assertEquals("remote2", remote.getName());
        assertEquals(remote2URI.toURL().toString(), remote.getFetchURL());

        ex.expect(CommandSpecException.class);
        ex.expectMessage(RemoteException.StatusCode.REMOTE_ALREADY_EXISTS.toString());

        buildCommand(options).run(testContext.get());
    }
}
