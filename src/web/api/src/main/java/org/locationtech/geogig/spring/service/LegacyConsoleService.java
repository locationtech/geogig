/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.locationtech.geogig.cli.ArgumentTokenizer;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.porcelain.ConfigGet;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.ConsoleRunCommandResponse;
import org.locationtech.geogig.spring.dto.ConsoleRunCommandResponse.ConsoleError;
import org.springframework.stereotype.Service;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.CharSource;
import com.google.common.io.FileBackedOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 */
@Service("legacyConsoleService")
public class LegacyConsoleService extends AbstractRepositoryService {

    public ConsoleRunCommandResponse runCommand(RepositoryProvider provider, String repoName,
            InputStream input) {
        final Repository repository = getRepository(provider, repoName);
        final Reader body = new InputStreamReader(input);
        final JsonParser parser = new JsonParser();
        final JsonElement jsonElement = parser.parse(body);
        Preconditions.checkArgument(jsonElement.isJsonObject(), "Json body must be supplied.");
        final JsonObject json = jsonElement.getAsJsonObject();
        Preconditions.checkArgument("2.0".equals(json.get("jsonrpc").getAsString()));
        ConsoleRunCommandResponse response;
        if (!checkConsoleEnabled(repository.context())) {
            response = serviceDisabled(json);
        } else {
            response = processRequest(json, repository);
        }
        return response;
    }

    private ConsoleRunCommandResponse processRequest(JsonObject json, final Repository repo) {
        final String command = json.get("method").getAsString();
        final String queryId = json.get("id").getAsString();
        // not used, we're getting the whole command and args in the "method" object
        // JsonArray paramsArray = json.get("params").getAsJsonArray();

        InputStream in = new ByteArrayInputStream(new byte[0]);
        // dumps output to a temp file if > threshold
        FileBackedOutputStream out = new FileBackedOutputStream(4096);
        try {
            // pass it a BufferedOutputStream 'cause it doesn't buffer the internal FileOutputStream
            Console console = new Console(in, new BufferedOutputStream(out)).disableAnsi();
            Platform platform = repo.platform();

            GeoGIG geogig = new GeoGIG(repo);
            GeogigCLI geogigCLI = new GeogigCLI(geogig, console);
            geogigCLI.setPlatform(platform);
            geogigCLI.disableProgressListener();

            String[] args = ArgumentTokenizer.tokenize(command);
            final int exitCode = geogigCLI.execute(args);

            final int charCountLimit = getOutputLimit(repo.context());
            final StringBuilder output = getLimitedOutput(out, charCountLimit);
            String result = null;
            ConsoleError error = null;

            if (exitCode == 0) {
                result = output.toString();
            } else {
                Exception exception = geogigCLI.exception;
                error = buildError(exitCode, output, exception);
            }
            return new ConsoleRunCommandResponse(queryId, result, error);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // delete temp file
            try {
                out.reset();
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }
    }

    private ConsoleRunCommandResponse serviceDisabled(JsonObject request) {
        final String queryId = request.get("id").getAsString();

        String message = "Web-console service is disabled. Run 'geogig config web.console.enabled true' on a real terminal to enable it.";
        ConsoleError error = new ConsoleError(-1, message);

        return new ConsoleRunCommandResponse(queryId, null, error);
    }

    private boolean checkConsoleEnabled(Context ctx) {
        Optional<String> configOption = ctx.command(ConfigGet.class).setName("web.console.enabled")
                .call();

        boolean enabled = configOption.isPresent() && Boolean.parseBoolean(configOption.get());
        return enabled;
    }

    private int getOutputLimit(Context ctx) {
        final int defaultLimit = 1024 * 16;

        Optional<String> configuredLimit = ctx.command(ConfigGet.class).setName("web.console.limit")
                .call();
        int limit = defaultLimit;
        if (configuredLimit.isPresent()) {
            try {
                limit = Integer.parseInt(configuredLimit.get());
            } catch (NumberFormatException ignore) {
                //
                limit = defaultLimit;
            }
            if (limit < 1024) {
                limit = 1024;
            }
        }
        return limit;
    }

    private StringBuilder getLimitedOutput(FileBackedOutputStream out, final int limit)
            throws IOException {

        CharSource charSource = out.asByteSource().asCharSource(Charsets.UTF_8);
        BufferedReader reader = charSource.openBufferedStream();
        final StringBuilder output = new StringBuilder();
        int count = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append('\n');
            count += line.length();
            if (count >= limit) {
                output.append("\nNote: output limited to ").append(count)
                        .append(" characters. Run config web.console.limit <newlimit> to change the current ")
                        .append(limit).append(" soft limit.");
                break;
            }
        }
        return output;
    }

    private ConsoleError buildError(final int exitCode, final StringBuilder output,
            Exception exception) {

        if (output.length() == 0 && exception != null && exception.getMessage() != null) {
            output.append(exception.getMessage());
        }
        String message = output.toString();
        return new ConsoleError(Integer.valueOf(exitCode), message);
    }
}
