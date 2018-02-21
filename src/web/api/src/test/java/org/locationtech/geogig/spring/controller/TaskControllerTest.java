/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.AsyncContext.Status;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.io.Files;

public class TaskControllerTest extends AbstractControllerTest {

    @Test
    public void testTaskList() throws Exception {
        AsyncContext context = AsyncContext.get();
        AsyncTestOp op1 = new AsyncTestOp();
        op1.stop(13);
        AsyncCommand<?> command1 = context.run(op1, "Op1 Description");
        int op1TaskId = Integer.parseInt(command1.getTaskId());
        AsyncTestOp op2 = new AsyncTestOp();
        op2.stop(8);
        AsyncCommand<?> command2 = context.run(op2, "Op2 Description");
        int op2TaskId = Integer.parseInt(command2.getTaskId());
        AsyncTestOp op3 = new AsyncTestOp();
        AsyncCommand<?> command3 = context.run(op3, "Op3 Description");
        int op3TaskId = Integer.parseInt(command3.getTaskId());
        waitForCommandToFinish(command1);
        waitForCommandToFinish(command2);
        waitForCommandToStart(command3);

        MockHttpServletRequestBuilder taskListRequest = MockMvcRequestBuilders.get("/tasks.json");

        perform(taskListRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tasks").exists()).andExpect(jsonPath("$.tasks").isArray())
                .andExpect(
                        jsonPath("$.tasks[?(@.task.description == \'Op1 Description\')]").exists())
                .andExpect(jsonPath(
                        "$.tasks[?(@.task.description == \'Op1 Description\')].task.status")
                                .value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.tasks[?(@.task.description == \'Op1 Description\')].task.id")
                        .value(op1TaskId))
                .andExpect(jsonPath(
                        "$.tasks[?(@.task.description == \'Op1 Description\')].task.result.value")
                                .value(13))
                .andExpect(
                        jsonPath("$.tasks[?(@.task.description == \'Op2 Description\')]").exists())
                .andExpect(jsonPath(
                        "$.tasks[?(@.task.description == \'Op2 Description\')].task.status")
                                .value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.tasks[?(@.task.description == \'Op2 Description\')].task.id")
                        .value(op2TaskId))
                .andExpect(jsonPath(
                        "$.tasks[?(@.task.description == \'Op2 Description\')].task.result.value")
                                .value(8))
                .andExpect(
                        jsonPath("$.tasks[?(@.task.description == \'Op3 Description\')]").exists())
                .andExpect(jsonPath(
                        "$.tasks[?(@.task.description == \'Op3 Description\')].task.status")
                                .value(Status.RUNNING.name()))
                .andExpect(jsonPath("$.tasks[?(@.task.description == \'Op3 Description\')].task.id")
                        .value(op3TaskId));
    }

    @Test
    public void testTaskStatus() throws Exception {
        AsyncContext context = AsyncContext.get();
        AsyncTestOp op = new AsyncTestOp();
        AsyncCommand<?> command = context.run(op, "Op Description");
        command.getProgressListener().setProgress(0.5f);
        command.getProgressListener().setDescription("Progress Description");

        waitForCommandToStart(command);

        int taskId = Integer.parseInt(command.getTaskId());

        MockHttpServletRequestBuilder taskStatusRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.RUNNING.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").exists())
                .andExpect(jsonPath("$.task.progress.task").value("Progress Description"))
                .andExpect(jsonPath("$.task.progress.amount").value(0.5))
                .andExpect(jsonPath("$.task.result").doesNotExist());

        op.stop(42);

        waitForCommandToFinish(command);

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").doesNotExist())
                .andExpect(jsonPath("$.task.result").exists())
                .andExpect(jsonPath("$.task.result.value").value(42));
    }

    @Test
    public void testTaskCancel() throws Exception {
        AsyncContext context = AsyncContext.get();
        AsyncTestOp op = new AsyncTestOp();
        AsyncCommand<?> command = context.run(op, "Op Description");
        command.getProgressListener().setProgress(0.5f);
        command.getProgressListener().setDescription("Progress Description");

        waitForCommandToStart(command);

        int taskId = Integer.parseInt(command.getTaskId());

        MockHttpServletRequestBuilder taskStatusRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.RUNNING.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").exists())
                .andExpect(jsonPath("$.task.progress.task").value("Progress Description"))
                .andExpect(jsonPath("$.task.progress.amount").value(0.5))
                .andExpect(jsonPath("$.task.result").doesNotExist());

        op.setReturnValue(12);

        MockHttpServletRequestBuilder taskCancelRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));
        taskCancelRequest.param("cancel", "true");

        perform(taskCancelRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.CANCELLED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").doesNotExist())
                .andExpect(jsonPath("$.task.result").exists())
                .andExpect(jsonPath("$.task.result.cancelled").value(true));
    }

    @Test
    public void testTaskPrune() throws Exception {
        AsyncContext context = AsyncContext.get();
        AsyncTestOp op = new AsyncTestOp();
        AsyncCommand<?> command = context.run(op, "Op Description");
        command.getProgressListener().setProgress(0.5f);
        command.getProgressListener().setDescription("Progress Description");

        waitForCommandToStart(command);

        int taskId = Integer.parseInt(command.getTaskId());

        MockHttpServletRequestBuilder taskStatusRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.RUNNING.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").exists())
                .andExpect(jsonPath("$.task.progress.task").value("Progress Description"))
                .andExpect(jsonPath("$.task.progress.amount").value(0.5))
                .andExpect(jsonPath("$.task.result").doesNotExist());

        op.stop(42);

        waitForCommandToFinish(command);

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").doesNotExist())
                .andExpect(jsonPath("$.task.result").exists())
                .andExpect(jsonPath("$.task.result.value").value(42));

        // Result should still exist, but prune it after we get it this time
        taskStatusRequest.param("prune", "true");
        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"))
                .andExpect(jsonPath("$.task.progress").doesNotExist())
                .andExpect(jsonPath("$.task.result").exists())
                .andExpect(jsonPath("$.task.result.value").value(42));

        // Result should no longer exist
        perform(taskStatusRequest).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(false))
                .andExpect(jsonPath("$.response.error")
                        .value(String.format("Task not found: %d", taskId)));
    }

    @Test
    public void testTaskDownload() throws Exception {
        TemporaryFolder tmpFolder = new TemporaryFolder();
        tmpFolder.create();
        File testFile = tmpFolder.newFile("testFile.bin");
        ObjectId fakeObjectId = RevObjectTestSupport.hashString("fakeObjectId");
        Files.write(fakeObjectId.getRawValue(), testFile);

        AsyncContext context = AsyncContext.get();
        AsyncDownloadTestOp op = new AsyncDownloadTestOp(testFile);
        AsyncCommand<?> command = context.run(op, "Op Description");

        int taskId = Integer.parseInt(command.getTaskId());

        waitForCommandToFinish(command);

        MockHttpServletRequestBuilder taskStatusRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"));

        MockHttpServletRequestBuilder taskDownloadRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d/download", taskId));

        MvcResult result = perform(taskDownloadRequest).andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();

        ObjectId fromResponse = ObjectId
                .create(result.getResponse().getContentAsByteArray());
        assertEquals(fakeObjectId, fromResponse);

    }

    @Test
    public void testTaskDownloadMediaTypes() throws Exception {
        testMediaType("test.gpkg", Variants.GEOPKG_MEDIA_TYPE);
        testMediaType("test.json", MediaType.APPLICATION_JSON);
        testMediaType("test.xml", MediaType.TEXT_XML);
    }

    private void testMediaType(String fileName, MediaType expectedMediaType) throws Exception {
        TemporaryFolder tmpFolder = new TemporaryFolder();
        tmpFolder.create();
        File testFile = tmpFolder.newFile(fileName);

        AsyncContext context = AsyncContext.get();
        AsyncDownloadTestOp op = new AsyncDownloadTestOp(testFile);
        AsyncCommand<?> command = context.run(op, "Op Description");

        waitForCommandToFinish(command);

        int taskId = Integer.parseInt(command.getTaskId());

        MockHttpServletRequestBuilder taskStatusRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));

        perform(taskStatusRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value(Status.FINISHED.name()))
                .andExpect(jsonPath("$.task.description").value("Op Description"));

        MockHttpServletRequestBuilder taskDownloadRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d/download", taskId));

        perform(taskDownloadRequest).andExpect(status().isOk())
                .andExpect(content().contentType(expectedMediaType));
    }

    private void waitForCommandToStart(AsyncCommand<?> command) throws InterruptedException {
        while (command.getStatus().equals(Status.WAITING)) {
            Thread.sleep(100);
        }
    }

    private void waitForCommandToFinish(AsyncCommand<?> command) throws InterruptedException {
        while (!command.isDone()) {
            Thread.sleep(100);
        }
    }

    public static class AsyncTestOp extends AbstractGeoGigOp<Integer> {

        private AtomicBoolean stop = new AtomicBoolean(false);

        private Integer returnValue = 0;

        @Override
        protected Integer _call() {
            while (!stop.get()) {
                if (getProgressListener().isCanceled()) {
                    return null;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return returnValue;
        }

        public void stop(Integer returnValue) {
            setReturnValue(returnValue);
            stop.set(true);
        }

        public void setReturnValue(Integer returnValue) {
            this.returnValue = returnValue;
        }
    }

    public static class AsyncTestOpRepresentation extends AsyncCommandRepresentation<Integer> {

        public AsyncTestOpRepresentation(AsyncCommand<Integer> cmd, boolean cleanup) {
            super(cmd, cleanup);
        }

        @Override
        protected void writeResultBody(StreamingWriter w, Integer result)
                throws StreamWriterException {
            if (result != null) {
                w.writeElement("value", result);
            } else {
                w.writeElement("cancelled", true);
            }
        }

        public static class Factory implements CommandRepresentationFactory<Integer> {

            @Override
            public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
                return AsyncTestOp.class.isAssignableFrom(cmdClass);
            }

            @Override
            public AsyncCommandRepresentation<Integer> newRepresentation(AsyncCommand<Integer> cmd,
                    boolean cleanup) {

                return new AsyncTestOpRepresentation(cmd, cleanup);
            }

        }
    }

    public static class AsyncDownloadTestOp extends AbstractGeoGigOp<File> {

        private final File returnFile;

        public AsyncDownloadTestOp(File returnFile) {
            this.returnFile = returnFile;
        }

        @Override
        protected File _call() {
            return returnFile;
        }
    }
}
