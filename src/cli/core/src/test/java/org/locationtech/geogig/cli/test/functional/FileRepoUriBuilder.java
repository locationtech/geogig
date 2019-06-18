package org.locationtech.geogig.cli.test.functional;

import java.io.File;
import java.net.URI;

import org.locationtech.geogig.repository.Platform;

import com.google.common.base.Preconditions;

import cucumber.api.Scenario;

public class FileRepoUriBuilder extends TestRepoURIBuilder {

    public @Override void before(Scenario scenario) {
    }

    public @Override void after(Scenario scenario) {
    }

    public @Override URI buildRootURI(Platform platform) {
        return platform.pwd().toURI();
    }

    public @Override URI newRepositoryURI(String name, Platform platform) {
        Preconditions.checkState(platform != null, "platform not set");
        return new File(platform.pwd(), name).toURI();
    }

}
