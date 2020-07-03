/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing.remotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class RemoteRemoveOpTest extends RepositoryTestCase {

    @Before
    public final void setUpInternal() {
    }

    @Test
    public void testNullName() {
        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);
        assertThrows(RemoteException.class, remoteRemove.setName(null)::call);
    }

    @Test
    public void testEmptyName() {
        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);
        assertThrows(RemoteException.class, remoteRemove.setName("")::call);
    }

    @Test
    public void testRemoveNoRemotes() {
        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);
        assertThrows(RemoteException.class, remoteRemove.setName("remote")::call);
    }

    @Test
    public void testRemoveNonexistentRemote() {
        final RemoteAddOp remoteAdd = repo.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);

        assertThrows(RemoteException.class, remoteRemove.setName("nonexistent")::call);
    }

    @Test
    public void testRemoveRemote() {
        final RemoteAddOp remoteAdd = repo.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), deletedRemote.getFetchSpec());
    }

    @Test
    public void testRemoveRemoteWithRefs() {
        final RemoteAddOp remoteAdd = repo.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        String refName = Ref.REMOTES_PREFIX + remoteName + "/branch1";
        repo.command(UpdateRef.class).setName(refName).setNewValue(ObjectId.NULL).setReason("test")
                .call();

        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        Optional<Ref> remoteRef = repo.command(RefParse.class).setName(refName).call();

        assertFalse(remoteRef.isPresent());

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), deletedRemote.getFetchSpec());
    }

    @Test
    public void testRemoveRemoteWithNoURL() {
        final RemoteAddOp remoteAdd = repo.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final ConfigOp config = repo.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".url").call();

        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals("", deletedRemote.getFetchURL());
        assertEquals("", deletedRemote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), deletedRemote.getFetchSpec());
    }

    @Test
    public void testRemoveRemoteWithNoFetch() {
        final RemoteAddOp remoteAdd = repo.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final ConfigOp config = repo.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".fetch")
                .call();

        final RemoteRemoveOp remoteRemove = repo.command(RemoteRemoveOp.class);

        Remote deletedRemote = remoteRemove.setName(remoteName).call();

        assertEquals(remoteName, deletedRemote.getName());
        assertEquals(remoteURL, deletedRemote.getFetchURL());
        assertEquals(remoteURL, deletedRemote.getPushURL());
        assertEquals("", deletedRemote.getFetchSpec());
    }
}
