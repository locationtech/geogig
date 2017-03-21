package org.locationtech.geogig.web.api.index;

import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

public abstract class AbstractIndexWebOpTest extends AbstractWebOpTest {
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends AbstractWebAPICommand> T buildCommand(ParameterSet options) {
        T command = (T) IndexCommandBuilder.build(getRoute());
        if (options != null) {
            command.setParameters(options);
        }
        return command;
    }
}
