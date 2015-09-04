Web-API and GeoServer REST configuration 
========================================

GeoGig integrates well with the standard GeoServer REST configuration API in order to configure
vector data stores and layers from GeoGig repositories.

You can use a combination of GeoGig's own "Web API" and GeoServer's REST configuration API to
create a wide variety of scripts.

GeoGig's plugin Web-API
-----------------------

With the GeoGig GeoServer plug-in, unlike than with the ``geogig serve`` command, it is possible to expose
several repositories. To do so, the GeoGig plug-in installs REST entry points under the ``<geoserver context>/geogig``
context (e.g. ``http://localhost:8080/geoserver/geogig``).

That root context can be used to query which repositories are being served by GeoServer.

GeoSever managed repositories are uniquely identified by a UUID automatically assigned when the repository is first added
to the GeoServer configuration.

Each specific repository's Web-API is accessed through the ``<geosever context>/geogig/<repository id>`` entry point, at
a difference to the ``geogig serve`` command that exposes the repository at the root application context.

So, for example, whenever you would list the current HEAD's commits by querying ``http://localhost:8182/log`` if serving
a single repository with ``geogig serve``, the same command as served by GeoServer would be at ``http://localhost:8080/geoserver/geogig/<repository id>/log``. 

From that point on, the commands available are exactly the same then when using the standalone Web API.

Creating a GeoGig DataStore with REST
-------------------------------------

To create a GeoGig DataStore through the GeoServer REST API, you just need to follow the 
`standard procedure <http://docs.geoserver.org/stable/en/user/rest/api/datastores.html>`_, 
knowing which data store connection parameters to use.

That is, issuing a ``POST`` request to ``/workspaces/<ws>/datastores[.<format>]``, where the request body for the XML
representation is like:

::

   <dataStore>
      <name>${data store name}</name>
      <connectionParameters>
         <entry key="geogig_repository">${repository directory}</entry>
         <entry key="branch">${branch}</entry>
      </connectionParameters>
   </dataStore>

That's all the information needed to create a GeoGig data store.

* ${data store name} is the name to be given to the data store, which then will be accessible through ``/workspaces/<ws>/datastores/<data store name>``. 
* ${repository directory} is the full path to the repository in the server file system.
* ${branch} is optional, and represents the name of the branch the data store is going to serve its data from. If not given, defaults to using
whatever branch is the currently checked out one in the repository whenever the data store is used.

Quick example:
-------------

For the impatient, here's a very quick cheat sheet on how to create a datastore and layer for a repository.
Suppose you have a repository created at ``/data/myrepo`` in the file system and it contains a ``roads`` feature type tree, and GeoServer
has a workspace named ``ws1``:

::

   curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -d "<dataStore><name>my_geogig_repo</name><connectionParameters><entry key=\"geogig_repository\">/data/my_repo</entry></connectionParameters></dataStore>" http://localhost:8080/geoserver/rest/workspaces/ws1/datastores
   < HTTP/1.1 201 Created
   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -d "<featureType><name>roads</name></featureType>" http://localhost:8080/geoserver/rest/workspaces/ws1/datastores/my_geogig_repo/featuretypes
   < HTTP/1.1 201 Created

For a more thorough example take a look at the tutorial bellow.    

cURL tutorial
-------------

The following is a small tutorial on how to use a combination of GeoGig and GeoServer web API's to configure data stores and layers
from a GeoGig repositories.

Lets start by listing the available repositories, given there are none yet added to geoserver:

::

   $ curl -v -u admin:geoserver -H "Accept:text/xml" "http://localhost:8080/geoserver/geogig"
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <?xml version='1.0' encoding='UTF-8'?>
   <repositories/>

Got an empty list of repositories. Now lets create an empty repository somewhere in the file system:

::

   $ mkdir -p /data/repos/geoserver && cd /data/repos/geoserver
   $ geogig init rosario_osm
   Initialized empty Geogig repository in /data/repos/geoserver/rosario_osm/.geogig
   $ cd rosario_osm
   $ geogig config user.name "John Doe"
   $ geogig config user.email "John@example.com"

Note you can omit the ``geogig config`` commands above if you already have done so with the ``--global`` switch. We're configuring
it here in the local repository configuration just the sake of demonstration. Either way, both user.name and user.email must be configured
either locally or globally before you can perform any commit.

Now import some OpenStreetMap data and create an ``osm_roads`` and an ``osm_buildings`` feature type tree in the geogig repository
from the raw ``node`` and ``way`` OSM data. To do so, create a file named ``mappings.json`` with the following contents in the
repository directory:
   
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

With that in place, and the coordinates for the Rosario city, lets populate the repo and tell GeoGig to create
the ``osm_roads`` and ``osm_buildings`` feature type trees using the mapping file.

::

   $ geogig osm download --mapping mappings.json --bbox -33.0183 -60.7246 -32.8684 -60.6096
   Connecting to http://overpass-api.de/api/interpreter...
   Importing into GeoGig repo...
   79,770
   80,568 entities processed in 13.86 s
   
   Building trees for [node, osm_buildings, osm_roads, way]
   Trees built in 438.6 ms
   Staging features...
   100%
   Committing features...
   100%
   Processed entities: 80,568.
    Nodes: 65,808.
    Ways: 14,760
    
Verify the data is in there:

::

   $ gig log
   Commit:  8affe7aff71fca408d8281cfca71243ef36178e9
   Author:  John Doe <John@example.com>
   Date:    (1 minutes ago) 2014-10-08 20:10:25 -0300
   Subject: Updated OSM data
   $ geogig ls-tree
   osm_roads
   osm_buildings
   node
   way

Now lets create a workspace in geoserver to hold our data store:

::

   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -d "<workspace><name>geogigtest</name></workspace>" http://localhost:8080/geoserver/rest/workspaces
   > POST /geoserver/rest/workspaces HTTP/1.1
   < HTTP/1.1 201 Created

.. note::
   Beware of not calling your namespace ``geogig`` as it's "local workspace catalog" entry point will conflict with the ``/geogig`` REST API entry point.

Create the GeoGig data store called ``rosario_osm`` inside that workspace. To do so, create a file named ``datastore.xml`` in the
current directory with the following content (note the value of the ``geogig_repository`` connection parameter is the repository directory):

::

   <dataStore>
      <name>rosario_osm</name>
      <connectionParameters>
         <entry key="geogig_repository">/data/repos/geoserver/rosario_osm</entry>
      </connectionParameters>
   </dataStore>

The run:

::

   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -T datastore.xml http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores
   < HTTP/1.1 201 Created

And verify the data store exists:

::

   $ curl -v -u admin:geoserver -XGET -H "Accept: text/xml" http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_osm
   < HTTP/1.1 200 OK
   < Content-Type: application/xml
   <dataStore>
     <name>rosario_osm</name>
     <type>GeoGIG</type>
     <enabled>true</enabled>
     <workspace>
       <name>geogigtest</name>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/workspaces/geogigtest.xml" type="application/xml"/>
     </workspace>
     <connectionParameters>
       <entry key="geogig_repository">6d62a0fe-1d98-42ac-a8ac-169dbc6e778a</entry>
       <entry key="resolver">org.geogig.geoserver.config.GeoServerStoreRepositoryResolver</entry>
       <entry key="namespace">http://geogigtest</entry>
     </connectionParameters>
     <__default>false</__default>
     <featureTypes>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_osm/featuretypes.xml" type="application/xml"/>
     </featureTypes>

**Note** that the GeoGig GeoServer plugin has replaced the value of the ``geogig_repository`` connection parameter by the unique identifier
of the internal object that represents the repository, and has added a ``resolver`` connection parameter, which is used to instruct the
GeoGig DataStore implementation how to resolve the repository location.

When working with the REST API, you can use one or the other format indistinctly. If you create a second data store for the same
repository using the repository directory as parameter instead of the repository id and the "resolver" parameter, the GeoGig plugin
will realize they're the same repository and reuse the existing configuration, assigning the new data store the same repository id.

To verify so, lets create a branch in the repository and a new data store that uses that branch instead. To do so, copy the following
XML fragment to a file called ``datastore_branch.xml``, that has a different name, an extra ``branch`` connection parameter, and the
same repository directory:

::

   <dataStore>
   <name>rosario_experimental</name>
   <connectionParameters>
      <entry key="geogig_repository">/data/repos/geoserver/rosario_osm</entry>
      <entry key="branch">experimental</entry>
   </connectionParameters>
   </dataStore>

Then create the branch called ``experimental`` in the repository, call the GeoServer REST API to create the new data store, and
finally get the new repository information:

::

   $ geogig branch experimental
   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -T datastore_branch.xml http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores
   < HTTP/1.1 201 Created
   $ curl -u admin:geoserver -XGET -H "Accept: text/xml" http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_experimental
   <dataStore>
     <name>rosario_experimental</name>
     <type>GeoGIG</type>
     <enabled>true</enabled>
     <workspace>
       <name>geogigtest</name>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/workspaces/geogigtest.xml" type="application/xml"/>
     </workspace>
     <connectionParameters>
       <entry key="geogig_repository">6d62a0fe-1d98-42ac-a8ac-169dbc6e778a</entry>
       <entry key="branch">experimental</entry>
       <entry key="resolver">org.geogig.geoserver.config.GeoServerStoreRepositoryResolver</entry>
       <entry key="namespace">http://geogigtest</entry>
     </connectionParameters>
     <__default>false</__default>
     <featureTypes>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_experimental/featuretypes.xml" type="application/xml"/>
     </featureTypes>
   </dataStore>

You should note that the same repository identifier was assigned (in this case **6d62a0fe-1d98-42ac-a8ac-169dbc6e778a**).

Now you have two different data stores, served from the same geogig repository, at different branches. These two different branches may
have different feature type trees (i.e. "layers") or different versions of them.

Lets revisit the initial query in this tutorial, and check the list of available repositories using GeoGig's own REST API:

::

   $ curl -v -u admin:geoserver -H "Accept:text/xml" "http://localhost:8080/geoserver/geogig"
   < HTTP/1.1 200 OK
   <?xml version='1.0' encoding='UTF-8'?>
   <repositories>
      <repository>
         <id>6d62a0fe-1d98-42ac-a8ac-169dbc6e778a</id>
         <name>rosario_osm</name>
         <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/geogig/6d62a0fe-1d98-42ac-a8ac-169dbc6e778a.xml" type="application/xml"/>
      </repository>
   </repositories>
   $ curl -v -u admin:geoserver -H "Accept:text/xml" "http://localhost:8080/geoserver/geogig/6d62a0fe-1d98-42ac-a8ac-169dbc6e778a.xml"
   < HTTP/1.1 200 OK
   <?xml version='1.0' encoding='UTF-8'?>
   <repository>
      <id>6d62a0fe-1d98-42ac-a8ac-169dbc6e778a</id>
      <name>rosario_osm</name>
      <location>/data/repos/geoserver/rosario_osm</location>
   </repository>

Also make sure the repository contains the expected feature type trees using the ``ls-tree`` command:

::

   $ curl -v -u admin:geoserver -H "Accept:text/xml" "http://localhost:8080/geoserver/geogig/6d62a0fe-1d98-42ac-a8ac-169dbc6e778a/ls-tree"
   < HTTP/1.1 200 OK
   <response>
      <success>true</success>
      <node><path>osm_roads</path></node>
      <node><path>osm_buildings</path></node>
      <node><path>node</path></node>
      <node><path>way</path></node>
   </response>


Finally, lets create a GeoServer FeatureType and Layer for each of the ``osm_roads`` and ``osm_buildings`` feature type
trees:

::

   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -d "<featureType><name>osm_roads</name></featureType>" http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_osm/featuretypes
   < HTTP/1.1 201 Created
   $ curl -v -u admin:geoserver -XPOST -H "Content-type: text/xml" -d "<featureType><name>osm_buildings</name></featureType>" http://localhost:8080/geoserver/rest/workspaces/geogigtest/datastores/rosario_osm/featuretypes
   < HTTP/1.1 201 Created

The above requests create the feature types, which automatically create a layer with default settings for each one:

::

   $ curl -u admin:geoserver -XGET -H "Accept: text/xml" http://localhost:8080/geoserver/rest/layers
   <layers>
     <layer>
       <name>osm_roads</name>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/layers/osm_roads.xml" type="application/xml"/>
     </layer>
     <layer>
       <name>osm_buildings</name>
       <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geoserver/rest/layers/osm_buildings.xml" type="application/xml"/>
      </layer>
   </layers>

Changing the configuration of either the feature types or the layers is just a matter of following the regular GeoServer REST
API to do so.
See  `Feature types <http://docs.geoserver.org/stable/en/user/rest/api/featuretypes.html>`_ and `Layers <http://docs.geoserver.org/stable/en/user/rest/api/layers.html>`_
in the GeoServer user manual for more information. The GeoServer `cURL examples <http://docs.geoserver.org/stable/en/user/rest/examples/curl.html>`_
are also a good source of information.

