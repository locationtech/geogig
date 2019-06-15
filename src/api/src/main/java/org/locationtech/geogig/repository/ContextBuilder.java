package org.locationtech.geogig.repository;

import org.locationtech.geogig.model.PriorityService;

public interface ContextBuilder extends PriorityService {

    Context build();

    /**
     * @param hints a set of hints to pass over to the injector to be injected into components that
     *        can make use of it
     */
    Context build(Hints hints);

}