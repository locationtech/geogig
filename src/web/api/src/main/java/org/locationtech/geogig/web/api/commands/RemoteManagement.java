/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.util.List;

import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.plumbing.remotes.RemoteException;
import org.locationtech.geogig.plumbing.remotes.RemoteRemoveOp;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RemoteListOp;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.http.HttpStatus;

import com.google.common.base.Optional;

/**
 * Interface for the Remote operations in GeoGig.
 * 
 * Web interface for {@link RemoteListOp}, {@link RemoteRemoveOp}, {@link RemoteAddOp}
 */

public class RemoteManagement extends AbstractWebAPICommand {

    boolean list;

    boolean remove;

    boolean ping;

    boolean update;

    boolean verbose;

    String remoteName;

    String newName;

    String remoteURL;

    String username = null;

    String password = null;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setList(Boolean.valueOf(options.getFirstValue("list", "false")));
        setRemove(Boolean.valueOf(options.getFirstValue("remove", "false")));
        setPing(Boolean.valueOf(options.getFirstValue("ping", "false")));
        setUpdate(Boolean.valueOf(options.getFirstValue("update", "false")));
        setVerbose(Boolean.valueOf(options.getFirstValue("verbose", "false")));
        setRemoteName(options.getFirstValue("remoteName", null));
        setNewName(options.getFirstValue("newName", null));
        setRemoteURL(options.getFirstValue("remoteURL", null));
        setUserName(options.getFirstValue("username", null));
        setPassword(options.getFirstValue("password", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the list variable
     * 
     * @param list - true to list the names of your remotes
     */
    public void setList(boolean list) {
        this.list = list;
    }

    /**
     * Mutator for the remove variable
     * 
     * @param remove - true to remove the given remote
     */
    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    /**
     * Mutator for the ping variable
     * 
     * @param ping - true to ping the given remote
     */
    public void setPing(boolean ping) {
        this.ping = ping;
    }

    /**
     * Mutator for the update variable
     * 
     * @param update - true to update the given remote
     */
    public void setUpdate(boolean update) {
        this.update = update;
    }

    /**
     * Mutator for the verbose variable
     * 
     * @param update - true to show more info for each repo
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Mutator for the remoteName variable
     * 
     * @param remoteName - the name of the remote to add or remove
     */
    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    /**
     * Mutator for the newName variable
     * 
     * @param newName - the new name of the remote to update
     */
    public void setNewName(String newName) {
        this.newName = newName;
    }

    /**
     * Mutator for the remoteURL variable
     * 
     * @param remoteURL - the URL to the repo to make a remote
     */
    public void setRemoteURL(String remoteURL) {
        this.remoteURL = remoteURL;
    }

    /**
     * Mutator for the username variable
     * 
     * @param username - the username to access the remote
     */
    public void setUserName(String username) {
        this.username = username;
    }

    /**
     * Mutator for the password variable
     * 
     * @param password - the password to access the remote
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);
        if (list) {
            remoteList(context, geogig);
        } else if (ping) {
            remotePing(context, geogig);
        } else if (remove) {
            remoteRemove(context, geogig);
        } else if (update) {
            remoteUpdate(context, geogig);
        } else {
            remoteAdd(context, geogig);
        }
    }

    private void remoteAdd(CommandContext context, final Context geogig) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            throw new CommandSpecException("No remote was specified.");
        } else if (remoteURL == null || remoteURL.trim().isEmpty()) {
            throw new CommandSpecException("No URL was specified.");
        }
        final Remote remote;
        try {
            remote = geogig.command(RemoteAddOp.class).setName(remoteName).setURL(remoteURL)
                    .setUserName(username).setPassword(password).call();
        } catch (RemoteException re) {
            throw new CommandSpecException(re.statusCode.toString(),
                    HttpStatus.BAD_REQUEST);
        }
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("name", remote.getName());
                out.finish();
            }
        });
    }

    private void remoteUpdate(CommandContext context, final Context geogig) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            throw new CommandSpecException("No remote was specified.");
        } else if (remoteURL == null || remoteURL.trim().isEmpty()) {
            throw new CommandSpecException("No URL was specified.");
        }
        final Remote newRemote;
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(remoteName)) {
            newRemote = geogig.command(RemoteAddOp.class).setName(newName).setURL(remoteURL)
                    .setUserName(username).setPassword(password).call();
            geogig.command(RemoteRemoveOp.class).setName(remoteName).call();
        } else {
            geogig.command(RemoteRemoveOp.class).setName(remoteName).call();
            newRemote = geogig.command(RemoteAddOp.class).setName(remoteName).setURL(remoteURL)
                    .setUserName(username).setPassword(password).call();
        }
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("name", newRemote.getName());
                out.finish();
            }
        });
    }

    private void remoteRemove(CommandContext context, final Context geogig) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            throw new CommandSpecException("No remote was specified.");
        }
        final Remote remote;
        try {
            remote = geogig.command(RemoteRemoveOp.class).setName(remoteName).call();
        } catch (RemoteException e) {
            throw new CommandSpecException(e.statusCode.toString(),
                    HttpStatus.BAD_REQUEST);
        }
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("name", remote.getName());
                out.finish();
            }
        });
    }

    private void remotePing(CommandContext context, final Context geogig) {
        Optional<Remote> remote;
        try {
            remote = geogig.command(RemoteResolve.class).setName(remoteName).call();
        } catch (RemoteException re) {
            throw new CommandSpecException(re.statusCode.toString(),
                    HttpStatus.BAD_REQUEST);
        }
        boolean remotePingResponse = false;
        if (remote.isPresent()) {
            try (IRemoteRepo rr = geogig.command(OpenRemote.class).setRemote(remote.get())
                    .readOnly().call()) {
                rr.headRef();
                remotePingResponse = true;
            } catch (Exception e) {
                // Do nothing, we will write the response later.
            }
        }
        final boolean pingSuccess = remotePingResponse;
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRemotePingResponse(pingSuccess);
                out.finish();
            }
        });
    }

    private void remoteList(CommandContext context, final Context geogig) {
        final List<Remote> remotes = geogig.command(RemoteListOp.class).call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRemoteListResponse(remotes, verbose);
                out.finish();
            }
        });
    }

}
