Web API: Asynchronous tasks
===========================

Some web API commands can run for a long time depending on how much data they're dealing with, and hence,
are executed asynchronously, meaning that, once invoked, they start the job in a background thread and return immediately.

The response contains a unique identifier for the asynchronous task and information on the task's running status.
This task identifier can then be used to poll the status of the task by querying the ``/tasks/<task id>`` API endpoint.

If no task is given, e.g. by querying the ``/tasks`` endpoint, a list of all available tasks will be returned.

A running task can be canceled with the ``cancel=true`` argument. For example:

::

   http://localhost:8182/tasks/3?cancel=true

This request will ask the operation to cancel and return immediately. Note, however, that it may take some time for the cancellation request to complete, as the running task may be blocking on I/O or in a temporarily non-cancelable state. Polling the task repeatedly with the
``cancel=true`` parameter is not a problem, though.

Finished tasks, whether they finished successfully, an error occurred, or were canceled, will be kept in memory for 10 minutes in order to allow their status to be queried.
Finished tasks can be explicitly pruned with the ``prune=true`` parameter. For example:

::

   http://localhost:8182/tasks/3?prune=true

Prune has no effect on unfinished tasks.

Examples
^^^^^^^^

When the command is invoked, it returns a result such as the following XML-formatted response.

::

   <task>
      <id>5</id>
      <status>RUNNING</status>
      <description>{command description}</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/5.xml" type="application/xml"/>
      <progress>
         <task>Connecting to http://overpass-api.de/api/interpreter...</task>
         <amount>0.0</amount>
      </progress>
   </task>

Or the JSON format:

::

   {"task":
      {
         "id":5,
         "status":"RUNNING",
         "description":"osm download filter: null, bbox: -32.9621,-60.6309,-32.9541,-60.6154",
         "href":"http://localhost:8182/tasks/5.json"
         "progress" :
         {
             "task" : "Connecting to http://overpass-api.de/api/interpreter...",
             "amount" : 0.0
         }
      }
   }

Note, the ``atom:link`` in the XML response and the ``href`` property in the JSON response, represent the status polling URL for the specific task ID.

The ``status`` attribute will have one of the following values: ``WAITING``, ``RUNNING``, ``FINISHED``, ``FAILED``, ``CANCELLED``.

``WAITING`` means that there are other tasks being run in the thread pool and this one has been scheduled to be run as soon
as a spot frees up in the thread pool. The others are self-explanatory.

The content of the task query response varies slightly depending on the task status.

Running tasks may or may not contain a progress indicator. It will depend on the type of task and how far along the task is at the point the response is generated.

::

   <task>
      <id>4</id>
      <status>RUNNING</status>
      <description> Importing geopackage...</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/4.xml" type="application/xml"/>
      <progress>
         <task>Importing into GeoGig repo...</task>
         <amount>1397120.0</amount>
      </progress>
   </task>


Successfully finished tasks may contain a result summary which is specific to the command executed. The following is an example of the result for a successful OSM import operation.

::

   <task>
      <id>5</id>
      <status>FINISHED</status>
      <description>Importing geopackage....</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/5.xml" type="application/xml"/>
      <result>
         <OSMReport>
            <latestChangeset>17218621</latestChangeset>
            <latestTimestamp>1412698140574</latestTimestamp>
            <processedEntities>901852</processedEntities>
            <nodeCount>865542</nodeCount>
            <wayCount>35778</wayCount>
            <unpprocessedCount>4</unpprocessedCount>
         </OSMReport>
      </result>
   </task>

And failed tasks contain an exception report.

::

   <task>
      <id>7</id>
      <status>FAILED</status>
      <description>Importing geopackage...</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/7.xml" type="application/xml"/>
      <error>
         <message>Nothing to commit after bea73023a9452a9d64f64264d2911ce1ec2b47f2</message>
         <stackTrace>
         <![CDATA[
            org.locationtech.geogig.api.porcelain.NothingToCommitException: Nothing to commit after bea73023a9452a9d64f64264d2911ce1ec2b47f2
            at org.locationtech.geogig.api.porcelain.CommitOp._call(CommitOp.java:320)
            at org.locationtech.geogig.api.porcelain.CommitOp._call(CommitOp.java:57)
            at org.locationtech.geogig.api.AbstractGeoGigOp.call(AbstractGeoGigOp.java:133)
            at org.locationtech.geogig.osm.internal.OSMImportOp._call(OSMImportOp.java:234)
            at org.locationtech.geogig.osm.internal.OSMImportOp._call(OSMImportOp.java:75)
            at org.locationtech.geogig.api.AbstractGeoGigOp.call(AbstractGeoGigOp.java:133)
            at org.locationtech.geogig.osm.internal.OSMDownloadOp._call(OSMDownloadOp.java:155)
            at org.locationtech.geogig.osm.internal.OSMDownloadOp._call(OSMDownloadOp.java:28)
            at org.locationtech.geogig.api.AbstractGeoGigOp.call(AbstractGeoGigOp.java:133)
            at org.locationtech.geogig.rest.AsyncContext$CommandCall.call(AsyncContext.java:192)
            at java.util.concurrent.FutureTask.run(FutureTask.java:262)
            ...
         ]]>
         </stackTrace>
      </error>
   </task>
