/**
 * Provides GeoGig {@link org.locationtech.geogig.repository.AbstractGeoGigOp commands} for
 * repositories to interact with each other in order to replicate or synchronize their contents.
 * <p>
 * Both replication and synchronization make use of the
 * {@link org.locationtech.geogig.repository.Remote} configuration entity to define the access
 * points for a repository to connect to a remote repository and give it a user defined name (for
 * example, in the context of a repository, the one it was replicated from, is generally given the
 * name {@code origin}).
 * <p>
 * Replication, or cloning, is the process of creating a new repository that's an exact replica of
 * another one.
 * <p>
 * Synchronization is the process of updating the contents of a repository so it matches the
 * contents of a remote repository, or updating the contents of a remote repository so it matches
 * the contents of the local repository, or both.
 * <p>
 * One repository can have multiple "remotes", and synchronize to/from them.
 * <p>
 * This package defines two API's, a high level one used to issue replication/synchronization
 * commands, and a low-level one for extensibility in terms of the ability to connect to and
 * interact with remote repositories through different protocols.
 * <p>
 * <h2>High level API</h2> The high level API is composed of the following commands:
 * <ul>
 * <li>{@link org.locationtech.geogig.remotes.CloneOp CloneOp}: creates a new repository that's an
 * exact replica of another's revision graph reachable from its tips (branches and tags).
 * {@code CloneOp} relies on {@code FetchOp} to transfer the contents of the remote to the new
 * clone, and then creates new branches on the clone to match the refs in the remote's copy.
 * 
 * <li>{@link org.locationtech.geogig.remotes.pack.FetchOp FetchOp}: Synchronizes changes made on a
 * remote to the local repository's copy of the remote. Given a local and remote repository,
 * resolves what's missing in the local tha's present on the remote and updates the local repository
 * to match the local copy of the remote's contents. The local repository keeps track of the
 * contents of a remote's refs under the local's {@code refs/remotes/<remote name>/} namespace.
 * {@code FetchOp} relies on {@code SendPackOp} to transfer the revision objects from the remote to
 * the local, and then updates the refs in the local remote's copy to match the updated tips.
 * 
 * <li>{@link org.locationtech.geogig.remotes.PullOp PullOp}: Synchronizes changes made on a remote
 * to the revision history of the local repository. In addition to matching the contents of a remote
 * with the local "copy" of the remote under {@code refs/remotes/<remote name>/}, updates the local
 * repository branches that are tracking the remote branches on the local repository. That is, makes
 * the revision history of the local repository match the revision history of the remote, not simply
 * updating the remote repository state in the local repository's reference.
 * 
 * {@code PullOp} relies on {@code FetchOp} to update the remote changes on the local.
 * 
 * <li>{@link org.locationtech.geogig.remotes.pack.PushOp PushOp}: Synchronizes changes made to the
 * local repository to the remote repository, uploading the missing objects in the remote's revision
 * graph and updating the remote refs to match the uploaded contents. {@code PuschOp} relies on
 * {@code SendPackOp} to transfer the revision objects from the local to the remote, and then
 * updates the refs in the remote to match the updated tips on the local.
 * 
 * <li>{@link org.locationtech.geogig.remotes.pack.SendPackOp SendPackOp} sends
 * {@link org.locationtech.geogig.model.RevObject revision objects} from a source repository to a
 * target repository in order for the target to complete the revision graph that satisfies a
 * {@link org.locationtech.geogig.remotes.pack.PackRequest PackRequest}. The PackRequest provides
 * the information needed to determine which objects are missing in the target revision graph and
 * will need to be sent in a SendPackOp. Relies on
 * {@link org.locationtech.geogig.remotes.pack.PreparePackOp PreparePackOp} and
 * {@link org.locationtech.geogig.remotes.pack.ReceivePackOp ReceivePackOp}
 * 
 * <li>{@link org.locationtech.geogig.remotes.pack.PreparePackOp PreparePackOp} given a
 * {@link org.locationtech.geogig.remotes.pack.PackRequest PackRequest}, resolves all the missing
 * {@link org.locationtech.geogig.model.RevCommit commits} and
 * {@link org.locationtech.geogig.model.RevTag tags} that exist on the source repository and don't
 * exist on the target repository, and returns a {@link org.locationtech.geogig.remotes.pack.Pack
 * Pack} that will send all the revision objects to complete the revision graph on the receiving
 * repository (commits, tags,feature types, trees, and features).
 * 
 * <li>{@link org.locationtech.geogig.remotes.pack.ReceivePackOp ReceivePackOp} called by
 * {@code SendPackOp} on the target repository to ingest the revision objects in the {@code Pack},
 * saving them to its {@link org.locationtech.geogig.storage.ObjectDatabase ObjectDatabase}
 * </ul>
 * 
 * Command dependencies:
 * 
 * <pre>
 * {@code
 * 
 *   CloneOp      -->    FetchOp --> SendPackOp --> PreparePackOp
 *      \                  / ^        ^    \
 *       \--> UpdateRef <--  |       /      \--> ReceivePackOp
 *           ^   ^          /       /
 *          /    |         /       /
 *         |  PullOp------        /
 *         |                     /
 *         ---PushOp------------
 * }
 * </pre>
 * 
 * 
 * <h2>Low level API</h2> The low level remoting API provides abstractions to connect to and
 * interface with remote repositories.
 * <ul>
 * <li>{@link org.locationtech.geogig.remotes.OpenRemote OpenRemote} given a
 * {@link org.locationtech.geogig.repository.Remote Remote} configuration, connects to the remote
 * repository returning an instance of {@link org.locationtech.geogig.remotes.internal.IRemoteRepo
 * IRemoteRepo}, using {@link org.locationtech.geogig.remotes.internal.RemoteResolver
 * RemoteResolver}
 * 
 * <li>{@link org.locationtech.geogig.remotes.internal.IRemoteRepo IRemoteRepo} a facade to
 * interface with a remote repository, and execute RPC-like calls on them.
 * 
 * <li>{@link org.locationtech.geogig.remotes.internal.RemoteResolver RemoteResolver} an SPI
 * (Service Provider Interface) to resolve implementations of {@code IRemoteRepo} for a specific
 * kind of remote repository and/or communication protocol. For example, there could be an
 * implementation that knows how to interface with remote repositories through local repository
 * connections, or HTTP, GRPC, Named Pipes, etc.
 * </ul>
 */
package org.locationtech.geogig.remotes;