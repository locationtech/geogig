Upgrading to GeoGig 1.1.1
=========================

.. warning:: Ensure you have your repository database fully backed up before moving to GeoGig 1.1.1

.. note:: This guide centered around a PostgreSQL GeoGig backend.  If you are using a RocksDB backend, please modify the command so they point to your RocksDB.

Upgrading existing repositories
-------------------------------

Our recommendation is to use GeoGig 1.1.1 to `clone`
your existing repository into a new repository (in a new database).
We also strongly recommend first validating a test system before attempting to upgrade a production server.
Here is a simple process to do this;


#.  Backup your existing databases (use existing PostgreSQL tools)
#.  Create a new PostgreSQL database
#.  Use GeoGig 1.1.1 to `clone` the existing repository into the new database
    ::

       geogig clone "postgresql://OLDdatabase/<repo>" "postgresql://NEWdatabase/<repo>"

#.  Create QuadTree indexes in the new repository for the branches/layer required (see below)
#.  Setup your PG Cache sizes (see below)
#.  Upgrade your **test** server to GeoGig 1.1.1, pointing to the new database
#.  Test until you are satisfied that GeoGig is working in your environment and the datasets are working as expected
#.  Repeat steps 2-5 to set up a new production database
#.  Upgrade your production server to GeoGig 1.1.1, pointing to the new production database


Cache Configuration
-------------------

The cache's configuration has been greatly simplified.  Please follow these documentation links for how to configure it and see statistics:

`Cache runtime configuration <http://geogig.org/docs/start/runtime.html>`_
`GeoServer GUI cache configuration <http://geogig.org/docs/interaction/geoserver_ui.html#geogig-runtime-settings>`_

PostgreSQL driver upgrade
-------------------------

.. note:: If you are using GeoServer 2.12 or later, you do not have to upgrade the PostgreSQL driver version (its the new GeoServer default).

.. note:: You will get a `java.lang.IllegalStateException: PostgreSQL JDBC Driver version not supported by GeoGig: 9.4` if you have not upgraded your PostgreSQL driver.

GeoGig's networking communication has been greatly improved. Binary transfers have been improved by 25%!

For GeoServer versions 2.9 to 2.11, you must use a newer PostgreSQL JDBC Driver (version 42.1.1).  The upgrade is simple -
remove the old postgresql driver and add the new one.

If you are building with maven - you may have to add a dependency on the correct PostgreSQL driver to have it included.  You may also
have to use exclusions to prevent the old version from being added.  Manually check that *only* the new PostgreSQL driver is being used.
For help in seeing dependencies, you can use `mvn dependency:tree`.

::

   ...
   <exclusion>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
   </exclusion>
   ...

   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <version>42.1.1</version>
   </dependency>


Please see the `GeoServer plugin installation guide <http://geogig.org/docs/start/installation.html#geoserver-plug-in>`_ for detailed instructions.


Using GeoGig 1.1.1 in the Cloud
-------------------------------

GeoGig 1.1.1 has been updated to work with various GeoServer clustered environments.  This work mostly ensures that new repos
added (or modified) are known to all the GeoServers in the cluster.  GeoGig is already designed to allow multiple simultaneous
connections and modifications to the same PostgreSQL dataset.

Clustered - do not use RocksDB
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

GeoGIG should *only* be used with the PostgreSQL backend in a clustered environment.  The GeoGig RocksDB backend cannot be
used by more than one GeoGig process at a time and some cloud filesystems would not efficiently allow the files to be shared.

TODO: add POM/deployment information here for the postgresql/rocks providers

Clustered - GeoGig async operation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are some asynchronous operations in GeoGig - these operations start a process that can be monitored for completion.
Unfortunately, these operations are only known about on the node that they were started on.  In the general cloud case, the
process could be started on one node and the request to monitor for completion could be performed on another node
(who doesn't know anything about the async operation).

In a clustered environment, the async operation and monitoring requests must be directed to the same node.  This can be
done by either directly talking to a node or (better) having the load balancer direct the requests to the same node.

