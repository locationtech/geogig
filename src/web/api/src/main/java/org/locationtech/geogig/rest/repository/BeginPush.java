/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.rest.repository;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.locationtech.geogig.web.api.commands.PushManager;
import org.restlet.Context;
import org.restlet.data.ClientInfo;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

/**
 *
 */
public class BeginPush extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();

        variants.add(new BeginPushRepresentation());
    }

    private class BeginPushRepresentation extends WriterRepresentation {

        public BeginPushRepresentation() {
            super(MediaType.TEXT_PLAIN);
        }

        @Override
        public void write(Writer w) throws IOException {
            ClientInfo info = getRequest().getClientInfo();
            Form options = getRequest().getResourceRef().getQueryAsForm();

            // make a combined ip address to handle requests from multiple machines in the same
            // external network.
            // e.g.: ext.ern.al.IP.int.ern.al.IP
            String ipAddress = info.getAddress() + "." + options.getFirstValue("internalIp", "");
            PushManager pushManager = PushManager.get();
            pushManager.connectionBegin(ipAddress);
            w.write("Push began for address: " + ipAddress);
            w.flush();
        }
    }

}
