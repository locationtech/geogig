/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.plumbing.diff.VerifyPatchOp;
import org.locationtech.geogig.plumbing.diff.VerifyPatchResults;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.io.Closeables;

/**
 * Verifies that a patch can be applied
 * 
 */
@ReadOnly
@Parameters(commandNames = "verify-patch", commandDescription = "Verifies that a patch can be applied")
public class VerifyPatch extends AbstractCommand {

    /**
     * The path to the patch file
     */
    @Parameter(description = "<patch>")
    private List<String> patchFiles = new ArrayList<String>();

    @Parameter(names = { "--reverse" }, description = "Check if the patch can be applied in reverse")
    private boolean reverse;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(patchFiles.size() < 2, "Only one single patch file accepted");
        checkParameter(!patchFiles.isEmpty(), "No patch file specified");

        Console console = cli.getConsole();

        File patchFile = new File(patchFiles.get(0));
        checkParameter(patchFile.exists(), "Patch file cannot be found");
        FileInputStream stream;
        try {
            stream = new FileInputStream(patchFile);
        } catch (FileNotFoundException e1) {
            throw new IllegalStateException("Can't open patch file " + patchFile);
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Closeables.closeQuietly(reader);
            Closeables.closeQuietly(stream);
            throw new IllegalStateException("Error reading patch file " + patchFile, e);
        }
        Patch patch = PatchSerializer.read(reader);
        Closeables.closeQuietly(reader);
        Closeables.closeQuietly(stream);

        VerifyPatchResults verify = cli.getGeogig().command(VerifyPatchOp.class).setPatch(patch)
                .setReverse(reverse).call();
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

    }

}
