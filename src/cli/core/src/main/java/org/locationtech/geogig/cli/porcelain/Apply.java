/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.plumbing.diff.VerifyPatchOp;
import org.locationtech.geogig.plumbing.diff.VerifyPatchResults;
import org.locationtech.geogig.porcelain.ApplyPatchOp;
import org.locationtech.geogig.porcelain.CannotApplyPatchException;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

/**
 * Applies a patch that modifies the current working tree.
 * 
 * Patches are generated using the format-patch command, not with the diff command
 * 
 */
@Parameters(commandNames = "apply", commandDescription = "Apply a patch to the current working tree")
public class Apply extends AbstractCommand {

    /**
     * The path to the patch file
     */
    @Parameter(description = "<patch>")
    private List<String> patchFiles = new ArrayList<String>();

    /**
     * Check if patch can be applied
     */
    @Parameter(names = {
            "--check" }, description = "Do not apply. Just check that patch can be applied")
    private boolean check;

    @Parameter(names = { "--reverse" }, description = "apply the patch in reverse")
    private boolean reverse;

    /**
     * Whether to apply the patch partially and generate new patch file with rejected changes, or
     * try to apply the whole patch
     */
    @Parameter(names = {
            "--reject" }, description = "Apply the patch partially and generate new patch file with rejected changes")
    private boolean reject;

    @Parameter(names = {
            "--summary" }, description = "Do not apply. Just show a summary of changes contained in the patch")
    private boolean summary;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(patchFiles.size() < 2, "Only one single patch file accepted");
        checkParameter(!patchFiles.isEmpty(), "No patch file specified");

        Console console = cli.getConsole();
        GeoGIG geogig = cli.getGeogig();

        File patchFile = new File(patchFiles.get(0));
        checkParameter(patchFile.exists(), "Patch file cannot be found");
        FileInputStream stream;
        try {
            stream = new FileInputStream(patchFile);
        } catch (FileNotFoundException e1) {
            throw new CommandFailedException("Can't open patch file " + patchFile, true);
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Closeables.closeQuietly(reader);
            Closeables.closeQuietly(stream);
            throw new CommandFailedException("Error reading patch file " + patchFile, e);
        }
        Patch patch = PatchSerializer.read(reader);
        Closeables.closeQuietly(reader);
        Closeables.closeQuietly(stream);

        if (reverse) {
            patch = patch.reversed();
        }

        if (summary) {
            console.println(patch.toString());
        } else if (check) {
            VerifyPatchResults verify = cli.getGeogig().command(VerifyPatchOp.class).setPatch(patch)
                    .call();
            Patch toReject = verify.getToReject();
            Patch toApply = verify.getToApply();
            if (toReject.isEmpty()) {
                console.println("Patch can be applied.");
            } else {
                console.println("Error: Patch cannot be applied\n");
                console.println("Applicable entries:\n");
                console.println(toApply.toString());
                console.println("\nConflicting entries:\n");
                console.println(toReject.toString());
            }
        } else {
            try {
                Patch rejected = geogig.command(ApplyPatchOp.class).setPatch(patch)
                        .setApplyPartial(reject).call();
                if (reject) {
                    if (rejected.isEmpty()) {
                        console.println("Patch applied succesfully");
                    } else {
                        int accepted = patch.count() - rejected.count();
                        StringBuilder sb = new StringBuilder();
                        File file = new File(patchFile.getAbsolutePath() + ".rej");
                        sb.append("Patch applied only partially.\n");
                        sb.append(Integer.toString(accepted) + " changes were applied.\n");
                        sb.append(Integer.toString(rejected.count()) + " changes were rejected.\n");
                        BufferedWriter writer = Files.newWriter(file, Charsets.UTF_8);
                        PatchSerializer.write(writer, patch);
                        writer.flush();
                        writer.close();
                        sb.append("Patch file with rejected changes created at "
                                + file.getAbsolutePath() + "\n");
                        throw new CommandFailedException(sb.toString(), true);
                    }
                } else {
                    console.println("Patch applied succesfully");
                }
            } catch (CannotApplyPatchException e) {
                throw new CommandFailedException(e.getMessage(), true);
            }

        }

    }

}
