/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;

import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Optional;

/**
 * The interface for the Config operation in GeoGig.
 * 
 * Web interface for {@link ConfigOp}
 */

public class Config extends AbstractWebAPICommand {

    String name;

    String value;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setName(options.getFirstValue("name", null));
        setValue(options.getFirstValue("value", null));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.POST.equals(method) || RequestMethod.GET.equals(method)
                || super.supports(method);
    }


    /**
     * Mutator for the name variable
     * 
     * @param name - the name of the property
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Mutator for the value variable
     * 
     * @param value - the new value of the property
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        ConfigOp command = geogig.command(ConfigOp.class).setScope(ConfigScope.LOCAL);

        final ConfigAction action;
        if (context.getMethod() == RequestMethod.POST) {
            checkArgument(name != null, "You must specify the key when setting a config key.");
            checkArgument(value != null, "You must specify the value when setting a config key.");
            action = ConfigAction.CONFIG_SET;
            command.setName(name);
            command.setValue(value);
        } else {
            if (name == null) {
                action = ConfigAction.CONFIG_LIST;
            } else {
                action = ConfigAction.CONFIG_GET;
                command.setName(name);
            }
        }

        command.setAction(action);

        final Optional<Map<String,String>> results = command.call();
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                if (results.isPresent()) {
                    if (action == ConfigAction.CONFIG_LIST) {
                        out.writeConfigList(results.get().entrySet().iterator());
                    } else if (action == ConfigAction.CONFIG_GET) {
                        out.writeElement("value", results.get().get(name));
                    }
                }
                out.finish();
            }
        });
    }

}
