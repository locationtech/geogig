Upgrading to GeoGig 1.2.1
=========================

`1.2.1` is a maintenance release. It backports some fixes and improvements for managing GeoGig repositories in a PostgreSQL database. Hence you should only worry about an upgrade process if you have an existing GeoGig database in Postgres. On the contrary, if you're creating a new geogig database, there's nothing extra to do, GeoGig will create the correct database schema for you.

.. warning:: If you try to run GeoGig 1.2.1 against a database created with a previous version, an error message will be shown asking you to run the upgrade process:
    ::

     $ geogig --repo "postrgesql://localhost/geogig_repositories/somerepo?..." log
     ERROR: Database geogig_repositories is running an outdated geogig schema. You need to run `geogig postgres-upgrade` from the command line before continuing.


Important Changes in GeoGig 1.2.1
---------------------------------

There are only two changes or relevance in this release, both related to the PostgreSQL storage backend:

1. If the PostgreSQL version is `10.0` or higher, and you're creating a new GeoGig database, the DDL script that GeoGig runs to initialize it will now create `HASH` indexes for the `geogig_object_*` tables, where the revision objects (commits, tags, features, etc) are stored. This is because just since version 10.0, PostgreSQL HASH indexes are WAL safe, and they result in slightly better performance under load/with big datasets.

2. In order to fix an important bug, one of the geogig tables got a new column. For instance, `geogig_graph_edge` has a new `dstindex INT NOT NULL` column, which is used to ensure the parent commit ids of any given merge commit are returned in the correct order when performing a commit graph traversal. For this reason, a new command has being added to GeoGig's command line interface to aid in the upgrade process, both in order to run the required DDL script, and to re-build the commit graph for all the repositories in the database in one shot.

Upgrading to GeoGig 1.2.0
-------------------------

.. warning:: Just in case, ensure you have your repository database fully backed up before moving to GeoGig 1.2.1. `pg_dump` would do.

GeoGig 1.2.1 changes the underlying database format as explained above, hence we *strongly* recommend first validating a test system before attempting to upgrade a production server, and have your data properly backed up.

Here is a simple process to do this;

#.  Backup your existing databases (use existing PostgreSQL tools)
#.  Use GeoGig 1.2.1 to perform the geogig database upgrade as follows:
    ::

       geogig postgres-upgrade "postgresql://<server>[:port]/<database>?user=<dbuser>&password=<dbpassword>"

Here is an example output of running `postgres-upgrade`:

    ::

       $ geogig postgres-upgrade "postgresql://localhost:5432/geogig_repositories?user=geogig&password=..."

       Running DDL script:

       -- SCRIPT START --
       CREATE TABLE public.geogig_medatada (key TEXT PRIMARY KEY, value TEXT, description TEXT);
       INSERT INTO public.geogig_medatada (key, value) VALUES ('geogig.version', '1.2.1');
       INSERT INTO public.geogig_medatada (key, value) VALUES ('geogig.commit-id', 'f34c8dfc07454b7fd2fa339a7ae36ebdd5f97159');
       INSERT INTO public.geogig_medatada (key, value) VALUES ('schema.version', '1');
       INSERT INTO public.geogig_medatada (key, value) VALUES ('schema.features.partitions', '16');
       TRUNCATE public.geogig_graph_edge;
       ALTER TABLE public.geogig_graph_edge ADD COLUMN dstindex INT NOT NULL;
       -- SCRIPT END --
       
       Upgrading commit graph for all 17 repositories...
       Upgrading graph for repository reg_2016_wgs84_g_83ee687a
       Finished upgrading the geogig database to the latest version.

This will connect to the database and run the necessary DDL sentences, as well as rebuild the commit graph of all the repositories, which is not something that can be done purely in SQL, and would be the same than running `geogig rebuild-graph` for each of the repositories in the database.

Finally, if the database is up to date with the latest geogig postgres schema, a message saying so will be displayed:

    ::

       $ geogig postgres-upgrade "postgresql://localhost:5432/geogig_repositories?user=geogig&password=..."

       Database schema is up to date, checking for non DDL related upgrade actions...

       Nothing to upgrade. Database schema is up to date

Other Changes
-------------

Please see the `release notes <https://github.com/locationtech/geogig/releases/tag/v1.2.1>`_
