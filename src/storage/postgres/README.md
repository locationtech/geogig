This is the GeoGig storage backend for PostgreSQL databases.



USAGE:
------
Repository URL must be provided. From the command line, use ``geogig <command> <args> --repo <url>`` for every command
``<url>`` is of the form: ``postgresql://<server>[:<port>]/database[/<schema>]/<reponame>?user=<username>&password=<pwd>``

``port`` is optional, and defaults to ``5432`` if not given.
``schema`` is optional, and defaults to ``public`` if not given.

Example:

``$ geogig init --repo "postgresql://localhost/geogig/myrepo?user=postgres&password=secret``

Initializes a repository named ``myrepo`` in the PG instance running on localhost at port 5432, on the "geogig" database's public schema.


``$ geogig init --repo "postgresql://pg.test.com:1701/geogig/geogig_test/myrepo?user=postgres&password=secret``

Initializes a repository named ``myrepo`` in the PG instance running on pg.test.com at port 1701, on the "geogig" database's public "geogig_test" schema.


Tests
-----

Enable tests with the ``-P postgres`` maven profile: ``mvn clean install -P postgres``

Have a ``$HOME/.geogig-pg-backend-tests.properties`` file with the following content:

[postgres]
enabled = \<true|false>  
server = \<PG Host>  
port = \<PG Port>  
schema = \<PG Schema>  
database = \<PG Daatabase name>  
user = \<PG user>  
password = \<PG user password>  

For example;

[postgres]  
enabled = true  
server = localhost  
port = 5432  
schema = public  
database = testdb  
user = postgres  
password = postgres  
  
NOTE: ensure there are no extra spaces at the end of each line


A JUnit "Rule" called ``org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig`` is used
on each test class to create a config for each test.


IMPORTANT: every test case creates its own set of tables prefixed by "test_\<N>" where N is a random number between 0 and 999. Just for safety, its recommended to use a separate database schema to run the postgres backend tests. To do so, create the schema in pgsql:
$ psql -d geogig
# create schema geogig_tests;
# set search_path to geogig_tests;

And configure ``postgres.schema = geogig_tests`` in ``.geogig-pg-backend-tests.properties``.


PostgreSQL tuning
-----------------
Advise from http://www.slideshare.net/PGExperts/really-big-elephants-postgresql-dw-15833438

few max connections
===================

10 to 40 (note the default geogig connection pool is 10 connections, beware of concurrent instances)

This can be configured by setting the ``postgres.maxConnections`` property in the global config.

Example:

``$ geogig config —rootUri "postgresql://localhost/geogig?user=postgres&password=secret" —global postgres.maxConnections 20``



raise memory limits!
====================

shared_buffers = 1/8 to 1/4 of RAM
work_mem = 128MB to 1GB
mainteinance_work_mem = 512MB to 1GB
temp_buffers = 128MB to 1GB
effective_cache_size = 3/4 of RAM
wal_buffers = 16MB
