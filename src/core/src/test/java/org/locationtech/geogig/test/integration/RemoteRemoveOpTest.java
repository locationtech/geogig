/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.RemoteAddOp;
import org.locationtech.geogig.api.porcelain.RemoteException;
import org.locationtech.geogig.api.porcelain.RemoteRemoveOp;

import com.google.common.base.Optional;

public class RemoteRemoveOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public final void setUpInternal() {
    }

    @Test
    public void testNullName() {
        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        exception.expect(RemoteException.class);
        remoteRemove.setName(null).call();
    }

    @Test
    public void testEmptyName() {
        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        exception.expect(RemoteException.class);
        remoteRemove.setName("").call();
    }

    @Test
    public void testRemoveNoRemotes() {
        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        exception.expect(RemoteException.class);
        remoteRemove.setName("remote").call();
    }

    @Test
    public void testRemoveNonexistentRemote() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", remote.getFetch());

        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        exception.expect(RemoteException.class);
        remoteRemove.setName("nonexistent").call();
    }

    @Test
    public void testRemoveRemote() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", remote.getFetch());

        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", deletedRemote.getFetch());
    }

    @Test
    public void testRemoveRemoteWithRefs() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", remote.getFetch());

        String refName = Ref.REMOTES_PREFIX + remoteName + "/branch1";
        geogig.command(UpdateRef.class).setName(refName).setNewValue(ObjectId.NULL).call();

        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        Optional<Ref> remoteRef = geogig.command(RefParse.class).setName(refName).call();

        assertFalse(remoteRef.isPresent());

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", deletedRemote.getFetch());
    }

    @Test
    public void testRemoveRemoteWithNoURL() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", remote.getFetch());

        final ConfigOp config = geogig.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".url").call();

        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals("", deletedRemote.getFetchURL());
        assertEquals("", deletedRemote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", deletedRemote.getFetch());
    }

    @Test
    public void testRemoveRemoteWithNoFetch() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals("+refs/heads/*:refs/remotes/" + remoteName + "/*", remote.getFetch());

        final ConfigOp config = geogig.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".fetch")
                .call();

        final RemoteRemoveOp remoteRemove = geogig.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals("", deletedRemote.getFetch());
    }
}
