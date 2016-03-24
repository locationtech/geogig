OpenStreetMap Web-Api Documentation 
===================================

The OpenStreetMap Web API is an extension to the Web API that allows to import OSM xml and pbf data files, 
as well as to download data directly from the OSM servers and apply it to the repository, just like with 
the CLI's `osm import` and `osm download` commands.

It is highly recommended that any data manipulation command, including these ones, be run inside a transaction.
This is achieved by first initiating a transaction, then using the transaction identifier to run the command(s), 
and finally commit the transaction.
Example:

Initiate transaction:
::

   GET /<repo>/beginTransaction
   <response>
      <success>true</success>
      <Transaction><ID>7accffe5-d506-4273-96f2-7b2581b71b36</ID></Transaction>
   </response>
   GET /<repo>/<command>?transactionId=7accffe5-d506-4273-96f2-7b2581b71b36&<command args>
   GET /<repo>/endTransaction?transactionId=7accffe5-d506-4273-96f2-7b2581b71b36
   <response><success>true</success><Transaction/></response>
    
.. note:: 
   All osm-web-api command response are formatted for xml by default, however you can get a JSON response by addressing the resource with the ``.json`` extension.

.. note:: 
   Remember the entry point for a repository served by the ``geogig serve`` command is at the root context (e.g. ``http://localhost:8182/``)
   whilst when using the GeoServer plugin, its under ``<geoserver context>/geogig/<repository id>`` (e.g. ``http://localhost:8080/geoserver/geogig/1f563a96-bd5c-4d59-84ec-e02e158eba78/``).
   You can check the list of repository ids and names by accessing ``http://localhost:8080/geoserver/geogig/``.


OSM Download
------------

Downloads data from OSM and commits it to the repository.

::

   GET /<repo>/osm/download[.xml|.json]?<filter=<filter file path>|bbox=S,W,N,E>[&message=<commit message>][&update=true|false][&rebase=true|false][&mapping=<mapping file>][&transactionId=<transaction id>]


Parameters
^^^^^^^^^^
  
**filter:** 
Mandatory if ``bbox`` is not given. The filter file to use. Must  exist in the server file system 
and contain an `Overpass QL filter <http://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL>`_.
   
**bbox:**
Mandatory if ``filter`` is not given. The bounding box to use as filter, in WGS84 coordinates. Format: ``<S>,<W>,<N>,<E>``.

**message:**
Message for the commit to create.

**update:**
Boolean. Default: ``false``. Update the OSM data currently in the geogig repository

**rebase:**
Boolean. Default: ``false``. Use rebase instead of merge when updating. Can only be true if ``upate = true``.

**mapping:**
The file that contains the data mapping to use.


Examples   
^^^^^^^^

A bad argument:
::

   $curl -v "localhost:8182/osm/download?bbox=-32.9621,-60.6309,-32.9541,-60.6154&mapping=nonExistentMappingFile"
   < HTTP/1.1 400 Bad Request
   The specified mapping file does not exist
   Usage: GET <repo context>/osm/download?<[filter=<filterfile>]|[bbox=S,W,N,E]>[&message=<commit message>][&mapping=<mapping file>][&update=true|false*][&rebase=true|false*]
   Arguments:
    * filter: Optional, or mandatory if {@code bbox} is not give. The filter file to use. Must exist in the server filesystem and contain an Overpass QL filter.
    * bbox: Mandatory if {@code filter} is not given. The bounding box to use as filter, in WGS84 coordinates. Format: {@code <S>,<W>,<N>,<E>}.
    * message: Message for the commit to create.
    * update: Boolean. Default: false. Update the OSM data currently in the geogig repository.
    * rebase: Boolean. Default: false. Use rebase instead of merge when updating. Can only be true of update is true.
    * mapping: The file that contains the data mapping to use

Download OSM data inside a transaction, using a mapping file and a bounding box filter:
::

   $curl -v "http://localhost:8182/beginTransaction"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <Transaction>
         <ID>430f4052-8bbe-4fce-8578-c572f069be28</ID>
      </Transaction>
   </response>

   $curl -v "http://localhost:8182/osm/download.xml?transactionID=430f4052-8bbe-4fce-8578-c572f069be28&bbox=-32.9621,-60.6309,-32.9541,-60.6154&mapping=/home/groldan/buildings_and_roads.json"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <task>
      <id>13</id>
      <status>RUNNING</status>
      <transactionId>430f4052-8bbe-4fce-8578-c572f069be28</transactionId>
      <description>osm download filter: null, bbox: -32.9621,-60.6309,-32.9541,-60.6154, mapping: /home/groldan/buildings_and_roads.json, update: false, rebase: false, repository: file:/home/groldan/data/geoserver/data/repos/osm_history/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/geogig/tasks/12.xml" type="application/xml"/>
   </task>

   curl -v "http://localhost:8182/endTransaction?transactionId=430f4052-8bbe-4fce-8578-c572f069be28"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <Transaction/>
   </response>


The example above uses a mapping file in ``/home/groldan/buildings_and_roads.json`` with the following content to create
the ``osm_roads`` and ``osm_buildings`` feature type trees out of the imported OSM "nodes" and "ways".

::

   {"rules":
     [
       {
         "name":"osm_roads",
         "filter":{
            "highway":[
              "tertiary","residential","service","secondary","track","footway","path","unclassified","primary",
              "trunk","motorway","construction","proposed","cycleway","living_street","steps", "road", "pedestrian",
              "construction","bridleway","platform","proposed"]
         },
         "fields":{
           "geom":{"name":"geom", "type":"LINESTRING"},
           "name":{"name":"name", "type":"STRING"}
         }
       },
       {
         "name":"osm_buildings",
         "filter":{
           "geom":["closed"],
           "building":[
             "yes","residential","house","garage","detached","terrace","apartments", "hut", "industrial", "roof", "garages",
             "terrace", "farm_auxiliary", "commercial", "retail", "school", "church", "shed", "manufacture", "greenhouse",
             "farm","office","cabin","barn"],
           "aeroway":["terminal"]
         },
         "fields":{
           "geom":{"name":"geom","type":"POLYGON"},
           "status": {"name":"status", "type":"STRING"},
           "building":{"name":"building", "type":"STRING"}
         }
       }
     ]
   }

We can verify both trees have been created with the ``ls-tree`` command like in::

   $curl -v "http://localhost:8182/ls-tree"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <node><path>osm_roads</path></node>
      <node><path>osm_buildings</path></node>
      <node><path>node</path></node>
      <node><path>way</path></node>
   </response>

OSM Import
------------

Imports OSM data from a file into the working tree, and optionally creates a commit if a mapping file is used.

::

   GET /<repo>/osm/import[.xml|.json]?<uri=<file path>>[&add=true|false][&noRaw=true|false][&mapping=<mapping file>][&message=<commit message>][&transactionId=<transaction id>]


Parameters
^^^^^^^^^^

**uri:**
Mandatory. The path to the ``.pbf`` or ``.xml`` OSM data file to import.
   
**add:**
Boolean. Default: ``false``. If ``true``, append the new data to existing one, otherwise remove existing data before importing.

**mapping:**
The file that contains the data mapping to use.

**noRaw:**
Boolean. Default: ``false``. Only has effect if using a mapping file. A value of ``true`` indicates not to import the "raw" ``node`` and ``way`` data.

**message:**
Optional. Message for the commit to create. Only has effect if using a mapping file.



Examples   
^^^^^^^^

A bad argument:
***************

::

   $ curl -v "http://localhost:8182/osm/import?uri=/data/osm/geofabrik/nonexistent.osm.pbf"
   < HTTP/1.1 200 OK
   <?xml version='1.0' encoding='UTF-8'?>
   <task><id>4</id>
      <status>FAILED</status>
      <description>osm import /data/osm/geofabrik/nonexistent.osm.pbf, repository: file:/home/groldan/data/geoserver/geogig_pg/data/repos/osm_history/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/geogig/tasks/4.xml" type="application/xml"/>
      <error>
         <message>File does not exist: /data/osm/geofabrik/nonexistent.osm.pbf</message>
         <stackTrace>
            <![CDATA[java.lang.IllegalArgumentException: File does not exist: /data/osm/geofabrik/nonexistent.osm.pbf
            at com.google.common.base.Preconditions.checkArgument(Preconditions.java:125)
            at org.locationtech.geogig.osm.internal.OSMImportOp._call(OSMImportOp.java:205)
            ...
         ]]>
         </stackTrace>
      </error>
   </task>

Missing uri argument:
*********************

::
   
   $ curl -v "http://localhost:8182/osm/import?"
   < HTTP/1.1 400 Bad Request
   Missing parameter: uri
   Usage: GET <repo context>/osm/import?uri=<osm file URI>[&<arg>=<value>]+
   Arguments:
    * uri: Mandatory. URL or path to OSM data file in the server filesystem
    * add: Optional. true|false. Default: false. If true, do not remove previous data before importing.
    * mapping: Optional. Location of mapping file in the server filesystem
    * noRaw: Optional. true|false. Default: false. If true, do not import raw data when using a mapping
   * Connection #0 to host localhost left intact
    * message: Optional. Message for the commit to create.groldan@eva01:~/git/geogig/doc/manual[osm_web_api](18:15:57)$ 


Proper sequence, using a transaction:
*************************************

Begin transaction:
::

   $ curl -v "http://localhost:8182/beginTransaction"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <Transaction>
         <ID>e0007ea2-96f7-4e5b-bee1-74915929c461</ID>
      </Transaction>
   </response>

Call import:
::

   $ curl -v "http://localhost:8182/osm/import?uri=/data/osm/geofabrik/albania-latest.osm.pbf&transactionId=e0007ea2-96f7-4e5b-bee1-74915929c461"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <task>
      <id>5</id>
      <status>RUNNING</status>
      <transactionId>e0007ea2-96f7-4e5b-bee1-74915929c461</transactionId>
      <description>osm import /data/osm/geofabrik/albania-latest.osm.pbf, repository: file:/home/groldan/data/geoserver/geogig_pg/data/repos/osm_history/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/geogig/tasks/5.xml" type="application/xml"/>
      <progress>
         <task>Importing into GeoGig repo...</task>
         <amount>0.0</amount>
      </progress>
   </task>
    
Poll task status until it's FINISHED:
::

   $ curl -v "http://localhost:8080/geoserver/geogig/tasks/5.xml"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <task>
      <id>5</id>
      <status>FINISHED</status>
      <transactionId>e0007ea2-96f7-4e5b-bee1-74915929c461</transactionId>
      <description>osm import /data/osm/geofabrik/albania-latest.osm.pbf, repository: file:/home/groldan/data/geoserver/geogig_pg/data/repos/osm_history/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/geogig/tasks/5.xml" type="application/xml"/>
      <result>
         <OSMReport>
            <latestChangeset>17218621</latestChangeset>
            <latestTimestamp>1412716764087</latestTimestamp>
            <processedEntities>901852</processedEntities>
            <nodeCount>865542</nodeCount>
            <wayCount>35778</wayCount>
            <unpprocessedCount>4</unpprocessedCount>
         </OSMReport>
      </result>
   </task>

Add and commit:
::

   $ curl -v "http://localhost:8182/add?transactionId=e0007ea2-96f7-4e5b-bee1-74915929c461"
   
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <Add>Success</Add>
   </response>

   $ curl -v "http://localhost:8182/commit?transactionId=e0007ea2-96f7-4e5b-bee1-74915929c461&message=Import%20of%20albania%20OSM%20data&authorName=Gabriel%20Roldan&authorEmail=groldan@example.com"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <commitId>51135710eb71eef3171df40b1b281c7f67e2eac1</commitId>
      <added>901316</added>
      <changed>0</changed>
      <deleted>46609</deleted>
   </response>

End transaction:
::

   $ curl -v "http://localhost:8182/endTransaction?transactionId=e0007ea2-96f7-4e5b-bee1-74915929c461"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <Transaction/>
   </response>


Verify the commit is now on the current HEAD:
::
 
   $ curl -v "http://localhost:8182/log?limit=1"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <response>
      <success>true</success>
      <commit>
         <id>51135710eb71eef3171df40b1b281c7f67e2eac1</id>
         <tree>1c58bdfb208e6d76836564d443c2de0b7ab2f1f9</tree>
         <parents><id>02702a5d296c4d2024b48cf80f957ff575e82aed</id></parents>
         <author>
            <name>Gabriel Roldan</name>
            <email>groldan@example.com</email>
            <timestamp>1412717228309</timestamp>
            <timeZoneOffset>-10800000</timeZoneOffset>
         </author>
         <committer>
            <name>Gabriel Roldan</name>
            <email>groldan@example.com</email>
            <timestamp>1412717228309</timestamp>
            <timeZoneOffset>-10800000</timeZoneOffset>
         </committer>
         <message>
            <![CDATA[ Import of albania OSM data ]]>
         </message>
      </commit>
   </response>
