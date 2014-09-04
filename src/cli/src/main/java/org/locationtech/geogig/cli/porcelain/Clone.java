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
import java.util.List;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RemotesReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;

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
 * <li> {@code geogig clone [--branch <name>] <repository> [<directory>]}
 * </ul>
 * 
 * @see CloneOp
 */
@RemotesReadOnly
@RequiresRepository(false)
@Parameters(commandNames = "clone", commandDescription = "Clone a repository into a new directory")
public class Clone extends AbstractCommand implements CLICommand {

    @Parameter(names = { "-b", "--branch" }, description = "Branch to checkout when clone is finished.")
    private String branch;

    @Parameter(names = { "--depth" }, description = "Depth of the clone.  If depth is less than 1, a full clone will be performed.")
    private int depth = 0;

    @Parameter(names = { "-u", "--username" }, description = "user name")
    private String username = null;

    @Parameter(names = { "-p", "--password" }, description = "password")
    private String password = null;

    @Parameter(names = { "--filter" }, description = "Ini filter file.  This will create a sparse clone.")
    private String filterFile;

    @Parameter(names = { "--config" }, description = "Extra configuration options to set while preparing repository. Separate names from values with an equals sign and delimit configuration options with a colon. Example: storage.objects=bdbje:bdbje.version=0.1")
    private String config;

    @Parameter(description = "<repository> [<directory>]")
    private List<String> args;

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

        String repoURL = args.get(0).replace('\\', '/');

        File repoDir;
        {
            File currDir = cli.getPlatform().pwd();

            // Construct a non-relative repository URL in case of a local remote
            if (!repoURL.startsWith("http")) {
                File repo = new File(repoURL);
                if (!repo.isAbsolute()) {
                    repo = new File(currDir, repoURL).getCanonicalFile();
                }
                repoURL = repo.toURI().getPath();
            }

            if (args != null && args.size() == 2) {
                String target = args.get(1);
                File f = new File(target);
                if (!f.isAbsolute()) {
                    f = new File(currDir, target).getCanonicalFile();
                }
                repoDir = f;
            } else {
                String[] sp = repoURL.split("/");
                repoDir = new File(currDir, sp[sp.length - 1]).getCanonicalFile();
            }
            if (!repoDir.exists() && !repoDir.mkdirs()) {
                throw new CommandFailedException("Can't create directory "
                        + repoDir.getAbsolutePath());
            }
        }

        GeoGIG geogig = new GeoGIG(cli.getGeogigInjector(), repoDir);

        checkParameter(!geogig.command(ResolveGeogigDir.class).call().isPresent(),
                "Destination path already exists and is not an empty directory.");

        geogig.command(InitOp.class).setConfig(Init.splitConfig(config)).setFilterFile(filterFile)
                .call();

        cli.setGeogig(geogig);
        cli.getPlatform().setWorkingDir(repoDir);

        cli.getConsole().println("Cloning into '" + cli.getPlatform().pwd().getName() + "'...");

        CloneOp clone = cli.getGeogig().command(CloneOp.class);
        clone.setProgressListener(cli.getProgressListener());
        clone.setBranch(branch).setRepositoryURL(repoURL);
        clone.setUserName(username).setPassword(password);
        clone.setDepth(depth);

        clone.call();

        cli.getConsole().println("Done.");
    }
}
