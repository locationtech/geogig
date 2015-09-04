.. _geoserver_ui:

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
