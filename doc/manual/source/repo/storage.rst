.. _repo.storage:

Storage Back-ends
=================

GeoGig can store repository elements in different storage backends or databases.

There are currently two storage backends: one default backend for repositories in the local computer's file system, and the second is a PostgreSQL backend that stores all the repository data in a Postgres database.

Whether to use one or the other is a matter of choice and/or convenience. The default storage backend is recommended for single repositories, such as when you are working on a specific project in your local machine.

The PostgreSQL backend is recommended for server side deployments, such as a deployment where several repositories are served through GeoServer.

A common scenario is to have a remote GeoServer with several repositories that use the PostgreSQL backend to host them all, and a local clone of one of those repositories in your local machine, can take the local clone off-line or simply do work disconnected from the server, and then pull from and push changes to the remote repository.

Default storage backend
=======================

As of 1.0-RC3, the default storage backend uses a BerkeleyDB Java Edition key/value database for the main repository elements such as the revision and graph objects, and simple text files for configuration settings, refs, and merge conflicts.

.. note:: However, the BerkeleyDB database will be replaced by the GeoGig 1.0 final release, by a better performing database. Nonetheless, repositories created with the BerkeleyDB storage backend will still be supported through GeoGig's plug-in mechanism.

When a repository is created in the local filesystem, it uses a directory structure like the following:

.. code-block:: console

    user@localhost:/data/myrepository$ ls .geogig/ -F1
    config
    graph/
    HEAD
    hooks/
    log/
    objects/
    refs/
    STAGE_HEAD
    WORK_HEAD


In order to use the GeoGig command line for a specific local repository, you can either:

* Work inside the repository directory (the ``.geogig`` directory's parent), like in the example above;
* Use the ``--repo <path/to/repository>`` argument to the ``geogig`` command. e.g.: ``user@localhost:/home/user$ geogig --repo /data/myrepository log`` to list the commits in the current branch of the ``/data/myrepository`` repo.

PostgreSQL storage backend
==========================

Whilst the default storage backend can hold only one single repository per directory, the PostgreSQL storage backend is designed to host several repositories in the same database.

It stores everything that's needed in the database, making it "stateless" and hence the best choice for server deployments, since you can even have several GeoServer instances serving data from the same repositories, in a cluster-like deployment.

.. note::  The database DOES NOT need PostGIS. A plain PostgreSQL database is used as a pure key/value store for most needs. Beware that for performance reasons, PostgreSQL 9.4 or newer is recommended, since prior versions have a performance issue with hash indexing that has been fixed since.

PostgreSQL repository URI
-------------------------

Since a repository stored in PostgreSQL is stateless as far as the local machine is concerned, you need to specify the repository location for every console command you want to execute against it.

This is done through a URI (Universal Resource Identifier) of the folloing form:

``postgresql://<server>[:<port>]/<database>[/<schema>]/<reponame>?user=<username>&password=<pwd>``

In the above URI scheme, parts enclosed between ``<>`` symbols are mandatory, and parts enclosed between ``[]`` symbols are optional. These symbols do not have to be written as part of the actual URI.

* ``<server>``: The PostgreSQL server host or IP address
* ``[:port]``: The TCP port number the server is listening to for connections. Defaults to ``5432`` if not provided.
* ``<database>``: The name of the database in the server where geogig will store the repositories
* ``<schema>``: The PostgreSQL database schema, defaults to ``public``
* ``<reponame``: The name of the geogig repository to create
* ``<username>``: The PostgreSQL user name to connect as
* ``<pwd>``: The PostgreSQL user's password to connect with

Examples:

The following commands create two repositories, ``gold`` and ``QA`` in the same ``geogig`` database of the ``dbserver`` PostgreSQL instance:

.. code-block:: console

 user@localhost:/home/user$ geogig --repo "postgresql://dbserver/geogig/master/gold?user=peter&password=secret" init

 user@localhost:/home/user$ geogig --repo "postgresql://dbserver/geogig/master/QA?user=peter&password=secret" init


Tired already of typing the repository URI?
A nice trick is to use environment variables instead:

.. code-block:: console

 user@localhost:/home/user$ export gold="postgresql://dbserver/geogig/master/gold?user=peter&password=secret"
 user@localhost:/home/user$ export QA="postgresql://dbserver/geogig/master/QA?user=peter&password=secret"

 user@localhost:/home/user$ geogig --repo $gold init
 user@localhost:/home/user$ geogig --repo $QA init

Database set up
---------------

Geogig will create the needed tables the first time it's used against a given database. However, the database and user/role must already exist in PostgreSQL. You can use a pre-existing PostgreSQL role with administrative access to an existing database, or you can run the following steps and SQL script to create the geogig database and tables first:

:download:`geogig_postgres.sql <geogig_postgres.sql>`

.. code-block:: console

 user@localhost:/home/user$ su - postgres
 postgres@localhost: $ createdb geogig
 postgres@localhost: $ psql -d geogig -f geogig_postgres.sql


Finally, refer to the :ref:`PostgreSQL backed GeoGig repository <configure-new-postgres-repo>` to learn how to configure a repository in GeoServer.









