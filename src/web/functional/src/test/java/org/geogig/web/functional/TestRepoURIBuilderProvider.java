package org.geogig.web.functional;

import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;

public class TestRepoURIBuilderProvider {

    private static TestRepoURIBuilder URIBuilder = null;

    public static void setURIBuilder(TestRepoURIBuilder builder) {
        URIBuilder = builder;
    }

    public static TestRepoURIBuilder getURIBuilder() {
        return URIBuilder;
    }

}
