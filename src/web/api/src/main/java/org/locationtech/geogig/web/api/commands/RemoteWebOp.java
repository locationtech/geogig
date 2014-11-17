/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.porcelain.RemoteAddOp;
import org.locationtech.geogig.api.porcelain.RemoteException;
import org.locationtech.geogig.api.porcelain.RemoteListOp;
import org.locationtech.geogig.api.porcelain.RemoteRemoveOp;
import org.locationtech.geogig.api.porcelain.RemoteResolve;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Remote operations in GeoGig.
 * 
 * Web interface for {@link RemoteListOp}, {@link RemoteRemoveOp}, {@link RemoteAddOp}
 */

public class RemoteWebOp extends AbstractWebAPICommand {

    private boolean list;

    private boolean remove;

    private boolean ping;

    private boolean update;

    private boolean verbose;

    private String remoteName;

    private String newName;

    private String remoteURL;

    private String username = null;

    private String password = null;

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
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);
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
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeElement("name", remote.getName());
                    out.finish();
                }
            });
        } catch (RemoteException e) {
            context.setResponseContent(CommandResponse.error(e.statusCode.toString()));
        } catch (Exception e) {
            context.setResponseContent(CommandResponse.error("Aborting Remote Add"));
        }
    }

    private void remoteUpdate(CommandContext context, final Context geogig) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            throw new CommandSpecException("No remote was specified.");
        } else if (remoteURL == null || remoteURL.trim().isEmpty()) {
            throw new CommandSpecException("No URL was specified.");
        }
        final Remote newRemote;
        try {
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
        } catch (RemoteException e) {
            context.setResponseContent(CommandResponse.error(e.statusCode.toString()));
        } catch (Exception e) {
            context.setResponseContent(CommandResponse.error("Aborting Remote Update"));
        }
    }

    private void remoteRemove(CommandContext context, final Context geogig) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            throw new CommandSpecException("No remote was specified.");
        }
        final Remote remote;
        try {
            remote = geogig.command(RemoteRemoveOp.class).setName(remoteName).call();
        } catch (RemoteException e) {
            context.setResponseContent(CommandResponse.error(e.statusCode.toString()));
            return;
        } catch (Exception e) {
            context.setResponseContent(CommandResponse.error("Aborting Remote Remove"));
            return;
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
        Optional<Remote> remote = geogig.command(RemoteResolve.class).setName(remoteName).call();

        final boolean remotePingResponse;
        if (remote.isPresent()) {
            Optional<IRemoteRepo> remoteRepo = RemoteUtils.newRemote(
                    GlobalContextBuilder.builder.build(), remote.get(), null, null);
            Ref ref = null;
            if (remoteRepo.isPresent()) {
                try {
                    remoteRepo.get().open();
                    ref = remoteRepo.get().headRef();
                    remoteRepo.get().close();
                } catch (IOException e) {
                    // Do nothing, we will write the response later.
                }
            }
            remotePingResponse = ref != null;
        } else {
            remotePingResponse = false;
        }
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRemotePingResponse(remotePingResponse);
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
