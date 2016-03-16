USAGE:
------
Repository URL must be provided. From the command line, use ``geogig <command> <args> --repo <url>`` for every command
``<url>`` is of the form: ``postgresql://<server>[:<port>]/database[/<schema>]/<repoid>?user=<username>&password=<pwd>``

``port`` is optional, and defaults to ``5432`` if not given.
``schema`` is optional, and defaults to ``public`` if not given.

Example:

``$ geogig init --repo "postgresql://localhost/geogig/myrepo?user=postgres&password=secret``

Initializes a repository named ``myrepo`` in the PG instance running on localhost at port 5432, on the "geogig" database's public schema.


``$ geogig init --repo "postgresql://pg.test.com:1701/geogig/geogig_test/myrepo?user=postgres&password=secret``

Initializes a repository named ``myrepo`` in the PG instance running on pg.test.com at port 1701, on the "geogig" database's public "geogig_test" schema.


TODO:
-----

* **Per database RevObject Cache**
* **Ref Database LOCKING**
* Make connection pool configurable
* (x) Finish PG conflicts database
* (x) PG blob store
* (x) Storage format evolvability
* (x) Config database
* (x) Per database shared pool
* (x) Support non filesystem repo URL
** How to configure logging? where?
* Multitenancy:
** Database metadata (biggest supported serialization version, what else?)
**(x) repository key
*(x) Instant cloning

API changes:
------------
* (x) ResolveGeogigDir returns URI instead of URL

Other actions:
--------------

* (x) ADAPT GeoGigDataStore to work with non file based URIS (GeoGigDataStoreFactory looks up for a file)
* (x) Change Read/SaveMergeCommitMessageOp to use a repository facility for blob storage. It also needs to be transaction specific,
      the current implementation doesn't take transactions into account. The PG backend may use a per repository and transaction identified blobs table.
* (x) RebaseOp and RevertOp shall use the repository blobstore instead of resolving the "revert"
      folder in the filesystem. CLI's Conflicts.java resolved rebase folder as File rebaseFolder = new File(new File(dir), "rebase-apply");
* (x) Use of .geogig/osm folder in OSMHistoryImport.getBranchTrackingFile(), ResolveOSMLogfile, ResolveOSMMappingLogFolder
* Revisit AbstractMappedRemoteRepo. Contains a check for "Sparse clone works only against file system repositories."
* Improve ResolveGeogigDirTest and InitOpTest to take an argument repository URL
* (x) Make sure point cache uses correct tmp dir

Tests
-----

Have a ``$HOME/.geogig-pg-backend-tests.properties`` file with the following content:

postgres.server = <server>
postgres.port = <port>
postgres.schema = <schema>
postgres.database = <database>
postgres.user = <user>
postgres.password = <password>

A JUnit "Rule" called ``org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig`` is used
on each test class to create a config for each test.


IMPORTANT: every test case creates its own set of tables prefixed by "geogig_test_<N>" where N is a random number between 0 and 999. Just for safety, its recommended to use a separate database schema to run the
postgres backend tests. To do so, create the schema in pgsql:
$ psql -d geogig
# create schema geogig_tests;
# set search_path to geogig_tests;

And configure ``postgres.schema = geogig_tests`` in ``.geogig-pg-backend-tests.properties``.


PostgreSQL tuning
-----------------
Advise from http://www.slideshare.net/PGExperts/really-big-elephants-postgresql-dw-15833438

few max connections
===================

10 to 40 (note the geogig connection pool is up to 10, beware of concurrent instances)

raise memory limits!
====================

shared_buffers = 1/8 to 1/4 of RAM
work_mem = 128MB to 1GB
mainteinance_work_mem = 512MB to 1GB
temp_buffers = 128MB to 1GB
effective_cache_size = 3/4 of RAM
wal_buffers = 16MB
