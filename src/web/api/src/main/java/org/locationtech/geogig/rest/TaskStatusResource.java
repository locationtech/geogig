/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import static org.locationtech.geogig.rest.Variants.JSON;
import static org.locationtech.geogig.rest.Variants.XML;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;
import static org.locationtech.geogig.rest.repository.RESTUtils.getStringAttribute;

import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

/**
 * Resource for {@code /osm/tasks /tasksId[?params]}
 * <p>
 * Params:
 * <ul>
 * <li>If no taskId is given, returns a list of tasks, whether they're in a waiting, running,
 * finished, or failed status. Done tasks (either finished or failed) are automatically pruned after
 * 10 minutes.
 * <li>prune: boolean, whether to prune a finished task (requires a taskId)
 * <li>cancel: boolean, if true, an attempt to cancel the tasks given by {@code taskId} is made.
 * </ul>
 */
public class TaskStatusResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        List<Variant> variants = getVariants();
        variants.add(XML);
        variants.add(JSON);
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    @Override
    public Representation getRepresentation(Variant variant) {
        final Request request = getRequest();

        final String taskId = getStringAttribute(request, "taskId");
        final boolean prune = Boolean.valueOf(getRequest().getResourceRef().getQueryAsForm()
                .getFirstValue("prune"));
        final boolean cancel = Boolean.valueOf(getRequest().getResourceRef().getQueryAsForm()
                .getFirstValue("cancel"));
        final AsyncContext asyncContext = AsyncContext.get();

        MediaType mediaType = variant.getMediaType();
        final String rootPath = request.getRootRef().toString();

        if (Strings.isNullOrEmpty(taskId)) {
            Iterable<AsyncCommand<? extends Object>> all = asyncContext.getAll();
            return new TaskListResource(mediaType, rootPath, all);
        }

        Optional<AsyncCommand<?>> cmd;

        if (prune) {
            cmd = asyncContext.getAndPruneIfFinished(taskId);
        } else {
            cmd = asyncContext.get(taskId);
        }
        if (!cmd.isPresent()) {
            throw new RestletException("Task not found: " + taskId, Status.CLIENT_ERROR_NOT_FOUND);
        }

        AsyncCommand<?> command = cmd.get();
        if (cancel) {
            command.tryCancel();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // ignore
            }
            if (prune) {
                asyncContext.getAndPruneIfFinished(taskId);
            }
        }
        return Representations.newRepresentation(command, mediaType, rootPath);
    }

    private static class TaskListResource extends JettisonRepresentation {

        private Iterable<AsyncCommand<? extends Object>> all;

        public TaskListResource(MediaType mediaType, String baseURL,
                Iterable<AsyncCommand<? extends Object>> all) {
            super(mediaType, baseURL);
            this.all = all;
        }

        @Override
        protected void write(XMLStreamWriter w) throws XMLStreamException {
            w.writeStartElement("tasks");
            for (AsyncCommand<? extends Object> c : all) {
                AsyncCommandRepresentation<?> rep = Representations.newRepresentation(c,
                        getMediaType(), baseURL);
                rep.write(w);
            }
            w.writeEndElement();
        }

    }
}
