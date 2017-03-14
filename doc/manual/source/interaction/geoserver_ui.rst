.. _geoserver_ui:

GeoServer GUI configuration
===========================

A GeoServer extension is available to allow GeoServer to interact with a GeoGig repository and use it as a datastore. It enables a GeoGig repository to be exposed as a remote for cloning, pushing, and pulling, as well as to publish its data via OGC services (WMS/WFS/WMTS/etc). Each top-level tree (often called "feature tree") in a GeoGig repository corresponds to a GeoServer layer. GeoServer treats a GeoGig repository configured as a store in much the same way as it does a database.

Building/installing the GeoServer GeoGig extension
--------------------------------------------------

You can download the latest stable version of the GeoGig GeoServer plugin from the `GeoGig <http://www.geogig.org/>`_ home page.

In order to build it from sources, a GeoGig module is currently included in the 2.8.x branch of GeoServer's community extensions. To build it, clone the GeoServer GitHub repository.
::
    git clone git@github.com:geoserver/geoserver.git

Change into the ``geoserver`` directory:
::

    cd geoserver

Checkout the 2.8.x branch:
::

    git checkout 2.8.x

Change into the ``src`` directory:
::

    cd src

Build the Community Modules:
::

    mvn clean install -DskipTests assembly:attached -f community/release/pom.xml -P communityRelease

This will build all of the GeoServer Community modules, including the plugin for GeoGig. Once the assembly completes, you should have a plugin bundle here:
::

    geoserver/src/community/target/release/geoserver-2.8-SNAPSHOT-geogig-plugin.zip

To install the GeoGig extension, unzip the above bundle into the GeoServer ``WEB-INF/lib`` folder of your GeoServer install and **restart** GeoServer.
::

    unzip geoserver/src/community/target/release/geoserver-2.8-SNAPSHOT-geogig-plugin.zip -d <GeoServer install dir>/webapps/geoserver/WEB-INF/lib/

    <restart GeoServer>

You should now be able to configure GeoGig repositories and use them as datastores.


Configuring a GeoGig store in GeoServer
---------------------------------------

When GeoServer is built with GeoGig support, it will be available as a Store type in the GeoServer admin UI.

.. figure:: ../img/geogig-store.png

You can configure a store by:

.. _configure-datastore-create-new:

- Creating a brand new GeoGig repository (see :ref:`configure-new-repo`):

.. figure:: ../img/configure-geogig-repo-store-addNew.png

.. _configure-datastore-import-existing:

- Importing an existing GeoGig repository that has not yet been configured within GeoServer (see :ref:`import-existing-repo`):

.. figure:: ../img/configure-geogig-repo-store-import.png

- Selecting an existing GeoGig repository that has been previously configured within GeoServer:

.. figure:: ../img/configure-geogig-repo-store-existing.png

Regardless of the method used to create the datastore, you will need to publish each top-level tree as a layer, individually.

.. figure:: ../img/geogig-publish-layer.png

It may be necessary to specify the SRS for your data if it is not recognized by GeoServer.

.. figure:: ../img/configure-layer-declared-srs.png

.. _configure-new-repo:

Configuring a new GeoGig repository in GeoServer
------------------------------------------------

You can create new GeoGig repositories through the :ref:`Create new GeoGig datastore <configure-datastore-create-new>` page or by navigating to the `GeoGig Repositories` configuration page in the admin bar.

.. figure:: ../img/configure-new-geogig-repo.png

and selecting `Create new repository`

.. figure:: ../img/create-new-geogig-repo.png

On the GeoGig repository configuration page, you can choose which type of repository you want, either a :ref:`directory-backed GeoGig repository <configure-new-directory-repo>`, or a :ref:`PostgreSQL-backed GeoGig repository <configure-new-postgres-repo>`. A directory-backed repository will store GeoGig data in a directory on the GeoServer filesystem, while a PostgreSQL-backed repository will store the GeoGig information in a PostgreSQL database. The database can be running on the same server as GeoServer or it can be remote.

.. _configure-new-directory-repo:

Configuring a new directory-backed GeoGig repository
----------------------------------------------------

To configure a new GeoGig repository that is backed by the filesystem, select **Directory** from the **Repository Type** pull-down, enter a **Repository Name**, a **Parent Directory** and click "Save":

.. figure:: ../img/create-new-geogig-repo-directory.png

You can enter the parent directory manually or select one from a directory chooser dialog by clicking the **Browse...** link:

.. figure:: ../img/create-new-geogig-repo-directory-chooser.png

.. _configure-new-postgres-repo:

Configuring a new PostgreSQL-backed GeoGig repository
-----------------------------------------------------

To configure a new GeoGig repository that is backed by a PostgreSQL database, select **PostgreSQL** from the **Repository Type** pull-down, enter the relevant database connection parameters and click "Save".

.. figure:: ../img/create-new-geogig-repo-postgres.png

.. _import-existing-repo:

Importing an existing GeoGig repository in GeoServer
----------------------------------------------------

You can create new GeoGig repositories through the :ref:`Create new GeoGig datastore <configure-datastore-import-existing>` page or by navigating to the `GeoGig Repositories` configuration page in the admin bar

.. figure:: ../img/configure-new-geogig-repo.png

and selecting `Import an existing repository`

.. figure:: ../img/import-existing-geogig-repo.png

Just as when creating new repositories, you have the option to import existing Directory-backed repositories or PostgreSQL-backed repositories. Select the **Repository Type** and choose/enter the repository location details:

.. figure:: ../img/import-existing-geogig-repo-directory.png

   *Directory-backed Repository configuration*

.. figure:: ../img/import-existing-geogig-repo-postgres.png

   *PostgreSQL-backed Repository configuration*

Cloning, Pushing, and Pulling
----------------------------

Once GeoServer is configured with a GeoGig repository, you can address it over the network at a URL path of the form::

    http://<host>:<port>/geoserver/geogig/repos/<geogig name>

A sample url as configured in the screenshots above::

    http://localhost:8080/geoserver/geogig/repos/geogig_dir_repo

It is then possible to clone this repository::

    $ geogig clone http://localhost:8080/geoserver/geogig/repos/geogig_dir_repo geogig_dir_repo

Your clone will be configured with the GeoServer repository as a remote. This configuration is stored in .geogig/config in your clone::

    [remote\origin]
    url = http://localhost:9090/geoserver/geogig/repos/geogig_dir_repo
    fetch = +refs/heads/*:refs/remotes/origin/*

    [branches\master]
    remote = origin
    merge = refs/heads/master

It is now possible to push and pull from this remote repository. You can verify this works by testing with the freshly cloned repository.

::

    $ geogig push origin
    Nothing to push.

    $ geogig pull origin
    100%
    Already up to date.

Automated repository synchronization
------------------------------------

Repositories configured by GeoServer can be configured with remotes and Automated Repository Synchronization. TODO

.. _current-limitations:

Current limitations
-------------------

When using Directory-backed GeoGig repositories, the default underlying object database (BerkeleyDB) is single-user. While the repository is being exposed over the network by either the stand-alone server or by GeoServer, you will not be able to access the repository from the command line interface. The error is pretty clear about whats going on.

::

com.sleepycat.je.EnvironmentLockedException: (JE 5.0.58) /Users/jj0hns0n/data/gisdata-repo/.geogig/objects The environment
cannot be locked for single writer access. ENV_LOCKED: The je.lck file could not be locked. Environment is invalid and must
be closed.

**GeoGig repositories backed by PostgreSQL do not have this limitation.**
