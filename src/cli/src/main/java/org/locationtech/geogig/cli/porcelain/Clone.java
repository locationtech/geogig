/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RemotesReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Clones a repository into a newly created directory, creates remote-tracking branches for each
 * branch in the cloned repository (visible using {@code geogig branch -r}), and creates and checks
 * out an initial branch that is forked from the cloned repository's currently active branch.
 * <p>
 * After the clone, a plain {@code geogig fetch} without arguments will update all the
 * remote-tracking branches, and a {@code geogig pull} without arguments will in addition merge the
 * remote master branch into the current master branch, if any.
 * <p>
 * This default configuration is achieved by creating references to the remote branch heads under
 * {@code refs/remotes/origin} and by initializing {@code remote.origin.url} and
 * {@code remote.origin.fetch} configuration variables.
 * <p>
 * CLI proxy for {@link CloneOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig clone [--branch <name>] <repository> [<directory>]}
 * </ul>
 * 
 * @see CloneOp
 */
@RemotesReadOnly
@RequiresRepository(false)
@Parameters(commandNames = "clone", commandDescription = "Clone a repository into a new directory")
public class Clone extends AbstractCommand implements CLICommand {

    @Parameter(names = { "-b",
            "--branch" }, description = "Branch to checkout when clone is finished.")
    private String branch;

    @Parameter(names = {
            "--depth" }, description = "Depth of the clone.  If depth is less than 1, a full clone will be performed.")
    private int depth = 0;

    @Parameter(names = { "-u", "--username" }, description = "user name")
    private String username = null;

    @Parameter(names = { "-p", "--password" }, description = "password")
    private String password = null;

    @Parameter(names = {
            "--filter" }, description = "Ini filter file.  This will create a sparse clone.")
    private String filterFile;

    @Parameter(names = {
            "--config" }, description = "Extra configuration options to set while preparing repository. Separate names from values with an equals sign and delimit configuration options with a colon. Example: storage.objects=bdbje:bdbje.version=0.1")
    private String config;

    @Parameter(description = "<repository> [<directory>|<clone URI>]")
    private List<String> args = new ArrayList<>(2);

    /**
     * Executes the clone command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(args != null && args.size() > 0, "You must specify a repository to clone.");
        checkParameter(args.size() < 3, "Too many arguments provided.");
        if (filterFile != null) {
            checkParameter(branch != null,
                    "Sparse Clone: You must explicitly specify a remote branch to clone by using '--branch <branch>'.");
        }

        final URI remoteURI;
        final URI cloneURI;
        final Platform platform = cli.getPlatform();
        final String remoteArg = args.get(0);
        try {
            remoteURI = checkAbsolute(remoteArg, platform);
        } catch (URISyntaxException e) {
            throw new CommandFailedException("Can't parse remote URI '" + remoteArg + "'", true);
        }

        final String targetArg;
        if (args.size() == 2) {
            targetArg = args.get(1);
            try {
                cloneURI = checkAbsolute(targetArg, platform);
            } catch (URISyntaxException e) {
                throw new CommandFailedException("Can't parse target URI '" + targetArg + "'",
                        true);
            }
        } else {
            cloneURI = platform.pwd().toURI();
            targetArg = cloneURI.getPath();
        }

        RepositoryResolver cloneInitializer = RepositoryResolver.lookup(cloneURI);

        if (cloneInitializer.repoExists(cloneURI)) {
            URI resolvedURI = cloneURI;
            if ("file".equals(cloneURI.getScheme())) {
                resolvedURI = ResolveGeogigURI.lookup(new File(cloneURI)).or(cloneURI);
            }
            String msg = "Destination repository already exists: " + resolvedURI;
            throw new InvalidParameterException(msg);
        }

        cli.setRepositoryURI(cloneURI.toString());
        Context cloneContext = cli.getGeogigInjector();
        cloneInitializer.initialize(cloneURI, cloneContext);
        cli.setPlatform(cloneContext.platform());

        Console console = cli.getConsole();

        Repository cloneRepo = cloneContext.command(InitOp.class)
                .setConfig(Init.splitConfig(config)).setFilterFile(filterFile).call();
        try {
            console.println("Cloning into '" + targetArg + "'...");
            console.flush();

            CloneOp clone = cloneRepo.command(CloneOp.class);
            clone.setProgressListener(cli.getProgressListener());
            clone.setBranch(branch).setRepositoryURL(remoteURI.toString());
            clone.setUserName(username).setPassword(password);
            clone.setDepth(depth);

            clone.call();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof RepositoryConnectionException) {
                throw new CommandFailedException(e.getMessage(), true);
            }
            throw e;
        } finally {
            cloneRepo.close();
        }
        console.println("Done.");
    }

    private URI checkAbsolute(String repoUri, Platform platform) throws URISyntaxException {
        URI uri;

        uri = new URI(repoUri.replace('\\', '/').replaceAll(" ", "%20"));

        String scheme = uri.getScheme();
        if (null == scheme) {
            uri = new File(platform.pwd(), repoUri).toURI();
        } else if ("file".equals(scheme)) {
            File f = new File(uri);
            if (!f.isAbsolute()) {
                uri = new File(platform.pwd(), repoUri).toURI();
            }
        }
        return uri;
    }
}
