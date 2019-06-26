/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli;

import org.locationtech.geogig.cli.plumbing.Cat;
import org.locationtech.geogig.cli.plumbing.DiffTree;
import org.locationtech.geogig.cli.plumbing.Insert;
import org.locationtech.geogig.cli.plumbing.LsTree;
import org.locationtech.geogig.cli.plumbing.MergeBase;
import org.locationtech.geogig.cli.plumbing.RebuildGraph;
import org.locationtech.geogig.cli.plumbing.RevList;
import org.locationtech.geogig.cli.plumbing.RevParse;
import org.locationtech.geogig.cli.plumbing.ShowRef;
import org.locationtech.geogig.cli.plumbing.VerifyPatch;
import org.locationtech.geogig.cli.plumbing.WalkGraph;
import org.locationtech.geogig.cli.porcelain.Add;
import org.locationtech.geogig.cli.porcelain.Apply;
import org.locationtech.geogig.cli.porcelain.Blame;
import org.locationtech.geogig.cli.porcelain.Branch;
import org.locationtech.geogig.cli.porcelain.Checkout;
import org.locationtech.geogig.cli.porcelain.CherryPick;
import org.locationtech.geogig.cli.porcelain.Clean;
import org.locationtech.geogig.cli.porcelain.Commit;
import org.locationtech.geogig.cli.porcelain.Config;
import org.locationtech.geogig.cli.porcelain.Conflicts;
import org.locationtech.geogig.cli.porcelain.Diff;
import org.locationtech.geogig.cli.porcelain.FormatPatch;
import org.locationtech.geogig.cli.porcelain.Init;
import org.locationtech.geogig.cli.porcelain.Log;
import org.locationtech.geogig.cli.porcelain.Ls;
import org.locationtech.geogig.cli.porcelain.Merge;
import org.locationtech.geogig.cli.porcelain.Rebase;
import org.locationtech.geogig.cli.porcelain.Remove;
import org.locationtech.geogig.cli.porcelain.Reset;
import org.locationtech.geogig.cli.porcelain.Revert;
import org.locationtech.geogig.cli.porcelain.Show;
import org.locationtech.geogig.cli.porcelain.Squash;
import org.locationtech.geogig.cli.porcelain.Status;
import org.locationtech.geogig.cli.porcelain.Tag;
import org.locationtech.geogig.cli.porcelain.index.IndexCommandProxy;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "geogig", //
        mixinStandardHelpOptions = true, //
        usageHelpAutoWidth = true, //
        versionProvider = VersionProvider.class, //
        subcommands = { //
                // CommandLine.HelpCommand adds support for `geogig help` as well as --help given by
                // mixinStandardHelpOptions
                CommandLine.HelpCommand.class//
                , RevParse.class//
                , Add.class//
                , Apply.class//
                , Blame.class//
                , Branch.class//
                , Cat.class//
                , Checkout.class//
                , CherryPick.class//
                , Clean.class//
                , Commit.class//
                , Config.class//
                , Conflicts.class//
                , Diff.class//
                , DiffTree.class//
                , FormatPatch.class//
                , VerifyPatch.class//
                , Init.class//
                , Insert.class//
                , Log.class//
                , Ls.class//
                , LsTree.class//
                , Merge.class//
                , MergeBase.class//
                , Remove.class//
                , Status.class//
                , Rebase.class//
                , Reset.class//
                , Revert.class//
                , RevList.class//
                , Show.class//
                , ShowRef.class//
                , Squash.class//
                , Tag.class//
                , WalkGraph.class//
                , RebuildGraph.class//
                , IndexCommandProxy.class//
        }//
)
public class Geogig implements Runnable {

    private @Spec CommandSpec commandSpec;

    @Option(names = "--repo", description = "URI of the repository to execute the command against. Defaults to current directory.")
    private String repoURI;

    private GeogigCLI geogigCLI;

    public Geogig(GeogigCLI geogigCLI) {
        this.geogigCLI = geogigCLI;
    }

    public GeogigCLI getGeogigCLI() {
        this.geogigCLI.setRepositoryURI(repoURI);
        return this.geogigCLI;
    }

    public @Override void run() {
        CommandLine commandLine = commandSpec.commandLine();
        commandLine.usage(commandLine.getOut());
    }
}
