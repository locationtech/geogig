/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.BranchRenameOp;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * With no arguments the command will display all existing branches with the current branch
 * highlighted with the asterisk. The {@code -r} option will list only remote branches and the
 * {@code -a} option will list both local and remote branches. Adding the {@code --color} option
 * with the value of auto, always, or never will add or remove color from the listing. With the
 * {@code -v} option it will list the branches along with the commit id and commit message that the
 * branch is currently on.
 * <p>
 * With a branch name specified it will create a branch of off the current branch. If a start point
 * is specified as well then it will be created off of the given start point. If the -c option is
 * given it will automatically checkout the branch once it is created.
 * <p>
 * With the -d option with a branch name specified will delete that branch. You cannot delete the
 * branch that you are currently on, checkout a different branch to delete it. Also with the -d
 * option you can list multiple branches for deletion.
 * <p>
 * With the -m option you can specify an oldBranchName to rename with the given newBranchName or you
 * can rename the current branch by not specifying oldBranchName. With the --force option you can
 * rename a branch to a name that already exists as a branch, however this will delete the other
 * branch.
 * <p>
 * CLI proxy {@link BranchListOp}, {@link BranchCreateOp}, {@link BranchDeleteOp},
 * {@link BranchRenameOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig branch [-c] <branchname>[<startpoint>]}: Creates a new branch with the given
 * branchname at the specified startpoint and checks it out immediately
 * <li> {@code geogig branch [--color=always] [-v] [-a]}: Lists all branches (Local and Remote) in
 * color with commit id and commit message
 * <li> {@code geogig branch [-r]}: List only remote branches
 * <li> {@code geogig branch [--delete] <branchname>...}: Deletes all the branches listed unless HEAD
 * is pointing to it
 * <li> {@code geogig branch [--force] [--rename] [<oldBranchName>] <newBranchName>}: Renames a
 * branch specified by oldBranchName or current branch if no oldBranchName is given to newBranchName
 * </ul>
 * 
 * @see BranchListOp
 * @see BranchCreateOp
 * @see BranchDeleteOp
 * @see BranchRenameOp
 */
@Parameters(commandNames = "branch", commandDescription = "List, create, or delete branches")
public class Branch extends AbstractCommand implements CLICommand {

    @Parameter(description = "<branch name> [<start point>]")
    private List<String> branchName = Lists.newArrayList();

    @Parameter(names = { "--checkout", "-c" }, description = "automatically checkout the new branch when the command is used to create a branch")
    private boolean checkout;

    @Parameter(names = { "--delete", "-d" })
    private boolean delete = false;

    @Parameter(names = { "--orphan", "-o" }, description = "create an orphan branch")
    private boolean orphan = false;

    @Parameter(names = { "--force", "-f" }, description = "Force renaming/creating of a branch if the specified branc name already exists")
    private boolean force = false;

    @Parameter(names = { "--verbose", "-v",
            "Verbose output for list mode. Shows branch commit id and commit message." })
    private boolean verbose = false;

    @Parameter(names = { "--remote", "-r" }, description = "List or delete (if used with -d) the remote-tracking branches.")
    private boolean remotes = false;

    @Parameter(names = { "--all", "-a" }, description = "List all branches, both local and remote")
    private boolean all = false;

    @Parameter(names = { "--rename", "-m" }, description = "Rename branch ")
    private boolean rename = false;

    @Override
    public void runInternal(final GeogigCLI cli) throws IOException {
        final GeoGIG geogig = cli.getGeogig();

        final Console console = cli.getConsole();

        if (delete) {
            checkParameter(!branchName.isEmpty(), "no name specified for deletion");

            for (String br : branchName) {
                Optional<? extends Ref> deletedBranch;
                deletedBranch = geogig.command(BranchDeleteOp.class).setName(br).call();

                checkParameter(deletedBranch.isPresent(), "No branch called '%s'.", br);

                console.println(String.format("Deleted branch '%s'.", br));
            }
            return;
        }

        checkParameter(branchName.size() < 3, "too many arguments: %s", branchName);

        if (rename) {
            checkParameter(!branchName.isEmpty(), "You must specify a branch to rename.");

            if (branchName.size() == 1) {
                Optional<Ref> headRef = geogig.command(RefParse.class).setName(Ref.HEAD).call();
                geogig.command(BranchRenameOp.class).setNewName(branchName.get(0)).setForce(force)
                        .call();
                if (headRef.isPresent()) {
                    SymRef ref = (SymRef) headRef.get();
                    console.println("renamed branch '"
                            + ref.getTarget().substring(Ref.HEADS_PREFIX.length()) + "' to '"
                            + branchName.get(0) + "'");
                }
            } else {
                geogig.command(BranchRenameOp.class).setOldName(branchName.get(0))
                        .setNewName(branchName.get(1)).setForce(force).call();
                console.println("renamed branch '" + branchName.get(0) + "' to '"
                        + branchName.get(1) + "'");
            }
            return;
        }

        if (branchName.isEmpty()) {
            listBranches(cli);
            return;
        }

        final String branch = branchName.get(0);
        final String origin = branchName.size() > 1 ? branchName.get(1) : Ref.HEAD;

        Ref newBranch = geogig.command(BranchCreateOp.class).setName(branch).setForce(force)
                .setOrphan(orphan).setAutoCheckout(checkout).setSource(origin).call();

        console.println("Created branch " + newBranch.getName());
    }

    private void listBranches(GeogigCLI cli) throws IOException {
        final Console console = cli.getConsole();
        final GeoGIG geogig = cli.getGeogig();

        boolean local = all || !(remotes);
        boolean remote = all || remotes;

        ImmutableList<Ref> branches = geogig.command(BranchListOp.class).setLocal(local)
                .setRemotes(remote).call();

        final Ref currentHead = geogig.command(RefParse.class).setName(Ref.HEAD).call().get();

        final int largest = verbose ? largestLenght(branches) : 0;

        for (Ref branchRef : branches) {
            final String branchRefName = branchRef.getName();

            Ansi ansi = newAnsi(console);

            if ((currentHead instanceof SymRef)
                    && ((SymRef) currentHead).getTarget().equals(branchRefName)) {
                ansi.a("* ").fg(Color.GREEN);
            } else {
                ansi.a("  ");
            }
            // print unqualified names for local branches
            String branchName = refDisplayString(branchRef);
            ansi.a(branchName);
            ansi.reset();

            if (verbose) {
                ansi.a(Strings.repeat(" ", 1 + (largest - branchName.length())));
                ansi.a(branchRef.getObjectId().toString().substring(0, 8)).a(" ");

                Optional<RevCommit> commit = findCommit(geogig, branchRef);
                if (commit.isPresent()) {
                    ansi.a(messageTitle(commit.get()));
                }
            }

            console.println(ansi.toString());
        }
    }

    private String messageTitle(RevCommit commit) {
        String message = Optional.fromNullable(commit.getMessage()).or("");
        int newline = message.indexOf('\n');
        return newline == -1 ? message : message.substring(0, newline);
    }

    /**
     * @param branchRef
     * @return
     */
    private Optional<RevCommit> findCommit(GeoGIG geogig, Ref branchRef) {
        ObjectId commitId = branchRef.getObjectId();
        if (commitId.isNull()) {
            return Optional.absent();
        }
        RevCommit commit = geogig.getRepository().getCommit(commitId);
        return Optional.of(commit);
    }

    /**
     * @param branches
     * @return
     */
    private int largestLenght(ImmutableList<Ref> branches) {
        int len = 0;
        for (Ref ref : branches) {
            len = Math.max(len, refDisplayString(ref).length());

        }
        return len;
    }

    private String refDisplayString(Ref ref) {

        String branchName = ref.getName();
        if (branchName.startsWith(Ref.HEADS_PREFIX)) {
            branchName = ref.localName();
        } else if (branchName.startsWith(Ref.REMOTES_PREFIX)) {
            branchName = branchName.substring(Ref.REMOTES_PREFIX.length());
        }
        if (ref instanceof SymRef) {
            branchName += " -> " + Ref.localName(((SymRef) ref).getTarget());
        }
        return branchName;
    }
}
