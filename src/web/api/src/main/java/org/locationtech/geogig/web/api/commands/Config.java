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

import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.restlet.data.Method;

import com.google.common.base.Optional;

/**
 * The interface for the Config operation in GeoGig.
 * 
 * Web interface for {@link ConfigOp}
 */

public class Config extends AbstractWebAPICommand {

    String name;

    String value;

    public Config(ParameterSet options) {
        super(options);
        setName(options.getFirstValue("name", null));
        setValue(options.getFirstValue("value", null));
    }

    @Override
    public boolean supports(final Method method) {
        return Method.POST.equals(method) || Method.GET.equals(method) || super.supports(method);
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
        final Context geogig = this.getCommandLocator(context);

        ConfigOp command = geogig.command(ConfigOp.class).setScope(ConfigScope.LOCAL);

        final ConfigAction action;
        if (context.getMethod() == Method.POST) {
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
                        Iterator<Map.Entry<String, String>> it = results.get().entrySet()
                                .iterator();
                        while (it.hasNext()) {
                            Map.Entry<String, String> pairs = (Map.Entry<String, String>) it.next();
                            out.getWriter().writeStartElement("config");
                            out.writeElement("name", pairs.getKey());
                            out.writeElement("value", pairs.getValue());
                            out.getWriter().writeEndElement();
                        }
                    } else if (action == ConfigAction.CONFIG_GET) {
                        out.writeElement("value", results.get().get(name));
                    }
                }
                out.finish();
            }
        });
    }

}
