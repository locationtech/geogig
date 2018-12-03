/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.remotes.RemoteListOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.collect.ImmutableList;

public class RemoteListOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public final void setUpInternal() {
    }

    @Test
    public void testListNoRemotes() {
        final RemoteListOp remoteList = geogig.command(RemoteListOp.class);

        ImmutableList<Remote> allRemotes = remoteList.call();

        assertTrue(allRemotes.isEmpty());
    }

    @Test
    public void testListMultipleRemotes() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName1 = "myremote";
        String remoteURL1 = "http://test.com";

        String remoteName2 = "myremote2";
        String remoteURL2 = "http://test2.org";
        String branch = "mybranch";

        Remote remote = remoteAdd.setName(remoteName1).setURL(remoteURL1).call();

        assertEquals(remoteName1, remote.getName());
        assertEquals(remoteURL1, remote.getFetchURL());
        assertEquals(remoteURL1, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName1), remote.getFetchSpec());

        remote = remoteAdd.setName(remoteName2).setURL(remoteURL2).setBranch(branch).call();

        assertEquals(remoteName2, remote.getName());
        assertEquals(remoteURL2, remote.getFetchURL());
        assertEquals(remoteURL2, remote.getPushURL());
        assertEquals("+refs/heads/" + branch + ":refs/remotes/" + remoteName2 + "/" + branch,
                remote.getFetchSpec());

        final RemoteListOp remoteList = geogig.command(RemoteListOp.class);

        ImmutableList<Remote> allRemotes = remoteList.call();

        assertEquals(2, allRemotes.size());

        Remote firstRemote = allRemotes.get(0);
        Remote secondRemote = allRemotes.get(1);

        if (!firstRemote.getName().equals(remoteName1)) {
            // swap first and second
            Remote tempRemote = firstRemote;
            firstRemote = secondRemote;
            secondRemote = tempRemote;
        }

        assertEquals(remoteName1, firstRemote.getName());
        assertEquals(remoteURL1, firstRemote.getFetchURL());
        assertEquals(remoteURL1, firstRemote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName1), firstRemote.getFetchSpec());

        assertEquals(remoteName2, secondRemote.getName());
        assertEquals(remoteURL2, secondRemote.getFetchURL());
        assertEquals(remoteURL2, secondRemote.getPushURL());
        assertEquals("+refs/heads/" + branch + ":refs/remotes/" + remoteName2 + "/" + branch,
                secondRemote.getFetchSpec());
    }

    @Test
    public void testListRemoteWithNoURL() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final ConfigOp config = geogig.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".url").call();

        final RemoteListOp remoteList = geogig.command(RemoteListOp.class);

        ImmutableList<Remote> allRemotes = remoteList.call();

        assertTrue(allRemotes.isEmpty());
    }

    @Test
    public void testListRemoteWithNoFetch() {
        final RemoteAddOp remoteAdd = geogig.command(RemoteAddOp.class);

        String remoteName = "myremote";
        String remoteURL = "http://test.com";

        Remote remote = remoteAdd.setName(remoteName).setURL(remoteURL).call();

        assertEquals(remoteName, remote.getName());
        assertEquals(remoteURL, remote.getFetchURL());
        assertEquals(remoteURL, remote.getPushURL());
        assertEquals(Remote.defaultRemoteRefSpec(remoteName), remote.getFetchSpec());

        final ConfigOp config = geogig.command(ConfigOp.class);
        config.setAction(ConfigAction.CONFIG_UNSET).setName("remote." + remoteName + ".fetch")
                .call();

        final RemoteListOp remoteList = geogig.command(RemoteListOp.class);

        ImmutableList<Remote> allRemotes = remoteList.call();

        assertTrue(allRemotes.isEmpty());
    }
}
