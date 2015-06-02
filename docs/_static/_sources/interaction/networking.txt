.. _networking:

GeoGig Networking
=================

In order for geogig clients to be able interact with a repository remotely, it must be exposed over a network as a service. This can  be accomplished in 2 different ways both of which are still under active development. With a local clone of the geogig source tree, mvn can be used to start a development server, and GeoServer can be built with GeoGig support. Both methods are described below.

More background information on GeoGig remotes can be found elsewhere in the manual and the technical docs.

Standalone Testing Server
=========================

To start the geogig network develoment server from a local clone of the GeoGig source tree in the src/parent directory::

    mvn jetty:run -pl ../web/app -f pom.xml -Dorg.geogig.web.repository=/Users/jj0hns0n/data/gisdata-repo/
    
Output similar to the following indicates everything is working correctly.
::

    [INFO] --- jetty-maven-plugin:7.1.6.v20100715:run (default-cli) @ geogig-web-app ---
    [INFO] Configuring Jetty for project: GeoGig WebApp
    [INFO] webAppSourceDirectory /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp does not exist. Defaulting to /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp
    [INFO] Reload Mechanic: automatic
    [INFO] Classes = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/target/classes
    [INFO] Context path = /
    [INFO] Tmp directory = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/target/tmp
    [INFO] Web defaults = org/eclipse/jetty/webapp/webdefault.xml
    [INFO] Web overrides =  none
    [INFO] web.xml file = file:/Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp/WEB-INF/web.xml
    [INFO] Webapp directory = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp
    [INFO] Starting jetty 7.1.6.v20100715 ...
    2013-05-27 13:36:45.081:INFO::jetty-7.1.6.v20100715
    2013-05-27 13:36:46.553:INFO::No Transaction manager found - if your webapp requires one, please configure one.
    2013-05-27 13:36:46.663:INFO::Started SelectChannelConnector@0.0.0.0:8080
    [INFO] Started Jetty Server
    [INFO] Starting scanner at interval of 5 seconds.

Test that the network service is working correctly by attempting to clone it.::

    $ geogig clone http://localhost:8080/geogig/ gisdata-repo-clone

Sample Log of the clone operation 
::

    2013-05-27 13:33:06.616:INFO:/:GeoGIG: [Restlet] Attaching application: org.geogig.web.Main@5fff83a9 to URI: /geogig null null
    May 27, 2013 1:33:06 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getdepth   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/manifest   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getdepth   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=2cdf52bcf5cf5aed78eab15ec56c81b3554136c7   200 -   0   3   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=2d74a9a48b2382a0fc77cbd72b4bde16e1e73b9b   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=3dbaa0649dfd166fb24ccd96739b8db5eb77da93   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=3840823f112a1667aee39f29fe2acba57042f9bc   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    POST    /geogig/repo/batchobjects   -   200 -   192 34  http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:58 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:58    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/manifest   -   200 -http://localhost:8080  Java/1.6.0_45   -

The getdepth, manifest, getparents and batchobjects operations performed are described in more detail in the technical documentation.

GeoServer
=========

A GeoServer extension is available to allow GeoServer to interact with a GeoGig repository and use it as data store. It enables a GeoGig repository to be exposed as a remote for cloning, pushing and pulling as well as via OGC services (WMS/WFS/WMTS etc). Each top level tree in a GeoGig repository corresponds to a GeoServer layer. GeoServer treats a GeoGig repository as a store in much the same way as it does a database.

Building/installing the GeoServer GeoGig extension
----------------------------------------------------


The GeoGig module is not currently included in GeoServer's community extensions. You can add it by including the following dependencies into your own pom.xml::

     <dependencies>
        <dependency>
          <groupId>org.geogig</groupId>
          <artifactId>geogig-web-api</artifactId>
          <version>0.9</version>
        </dependency>
        <dependency>
          <groupId>org.geogig</groupId>
          <artifactId>geogig-geotools</artifactId>
          <version>0.9</version>
        </dependency>
        <dependency>
          <groupId>org.geogig</groupId>
          <artifactId>geogig-geoserver</artifactId>
          <version>0.9</version>
        </dependency>
      </dependencies>

Include the plugin in your mvn build command::

    mvn clean install -Pgeogig

Deploy the resulting war in a servlet container.

Another way of installing the GeoGig extension is to build it yourself and then deploy it in the servlet container where you have deployed GeoServer.

The GeoServer GeoGig extension is found along with other GeoServer extensions in the geoserver-ext repository. 

- Clone the repository by running:

::

    git clone git@github.com:opengeo/geoserver-exts.git
    cd geoserver-exts
    mvn install


- In the ``geogig/target`` folder you will find a jar file named  ``gs-geogig-2.X-SNAPSHOT-shaded-plugin.jar``. Put that jar file in the GeoServer ``WEB-INF/lib`` folder. 

- Restart GeoServer.

The GeoGig data store should now be available.



Configuring a GeoGig Store in GeoServer
---------------------------------------

When GeoServer is built with GeoGig support, it will be available as a Store type in the GeoServer admin UI.

.. figure:: ../img/geogig-store.png

You can then configure a store by providing the path to the repository on the filesystem of the GeoServer installation. 

.. figure:: ../img/configure-geogig-repo-store.png

You will need to publish each top level tree as a layer individually.

.. figure:: ../img/geogig-publish-layer.png

It may be necessary to specify the SRS for your data it if is not recognized by GeoServer.

.. figure:: ../img/configure-layer-declared-srs.png


Cloning Pushing and Pulling
---------------------------

Once GeoServer is configured with this repository, you can address it over the network at a URL path of the form:: 

    http://<host>:<port>/geoserver/geogig/<workspace>:<store>

A sample url as configured in the screenshots above::

    http://localhost:8080/geoserver/geogig/topp:gisdata-repo

It is then possible to clone this repository::

    $ geogig clone http://localhost:8080/geoserver/geogig/topp:gisdata-repo gisdata-repo-clone

Your clone will be configured with the geoserver repository as a remote. This configuration is stored in .geogig/config in your clone::

    [remote\origin]
    url = http://localhost:8080/geoserver/geogig/topp:gisdata-repo
    fetch = +refs/heads/*:refs/remotes/origin/*
    
    [branches\master]
    remote = origin
    merge = refs/heads/master

It is now possible to push and pull from this remote repository. You can verify this works by testing with the freshly cloned repo::

    $ geogig push origin
    Nothing to push.
    
    $ geogig pull origin
    100%
    Already up to date.

Automated Repository Synchronization
------------------------------------

Repositories configured by GeoServer can be configured with remotes and Automated Repository Syncrhonization. TODO


Current Limitations
===================

The default underlying object database (berkeley db) is single user. While the repository is being exposed over the network by either the stand-alone server or by GeoServer, you will not be able to access the repo from the command line interface. The error is pretty clear about whats going on. 

com.sleepycat.je.EnvironmentLockedException: (JE 5.0.58) /Users/jj0hns0n/data/gisdata-repo/.geogig/objects The environment cannot be locked for single writer access. ENV_LOCKED: The je.lck file could not be locked. Environment is invalid and must be closed.
