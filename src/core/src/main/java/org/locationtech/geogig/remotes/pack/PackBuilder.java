package org.locationtech.geogig.remotes.pack;

import java.util.Set;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;

public interface PackBuilder {

    void start(Set<RevTag> tags);

    void startRefResponse(RefRequest req);

    void addCommit(RevCommit commit);

    void endRefResponse();

    Pack build();

}
