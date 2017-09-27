Web API: PostGIS Import
#######################


PostGIS Import (-T)
===================

The PostGIS import command allows for importing a data set from a PostGIS database into the working tree of a GeoGig repository.

::

   GET /repos/<repo>/postgis/import?fidAttrib=<attributeName>[&table=<tableName>][&all=<true|false>][&add=<true|false>][&forceFeatureType=<true|false>][&alter=<true|false>][&dest=<destinationTable>][&host=<dbHost>][&port=<dbPort>][&schema=<dbSchema>][&database=<dbName>][&user=<dbUser>][&password=<dbPassword>]

Parameters
----------

**fidAttrib:**
Mandatory.  The column to use as the feature id.  The values in this column should be unique.

**table:**
Optional.  The PostGIS table to import.  If specified, ``all`` must be set to ``false``.  If not specified, ``all`` must be set to ``true``.

**all:**
Optional.  If specified as ``true``, import all tables from the PostGIS database.

**add:**
Optional.  If specified as ``true``, only features that did not previously exist in the destination table will be imported.

**forceFeatureType:**
Optional.  If specified as ``true``, features will be added as they are, with their original feature type.

**alter:**
Optional.  Only valid when importing a single table.  If specified as ``true``, it does not overwrite, and modifies the existing features to have the same feature type as the imported table.

**dest:**
Optional.  The name of the table to import to.  If not specified, the name of the PostGIS table will be used.

**host:**
Optional.  The hostname of the PostGIS database.  If not specified, ``localhost`` will be used.

**port:**
Optional.  The port of the PostGIS database.  If not specified, ``5432`` will be used.

**schema:**
Optional.  The schema of the PostGIS database.  If not specified, ``public`` will be used.

**database:**
Optional.  The name of the PostGIS database.  If not specified, ``database`` will be used.

**user:**
Optional.  The name of the user to connect to the PostGIS database with.  If not specified, ``postgres`` will be used.

**password:**
Optional.  The password to connect to the PostGIS database with.  If not specified, no password will be used.

Examples
--------

**Import a table from a PostGIS database**

::

    $ curl -v "http://localhost:8182/repos/repo1/postgis/import?fidAttrib=fid&table=postgis_multipoly&database=postgis" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <task>
      <id>1</id>
      <status>RUNNING</status>
      <description>postgis import table postgis_multipoly into repository: file:/data/repos/7a4b140f-0703-43bc-942a-dfc60da9f3eb/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/1.xml" type="application/xml"/>
    </task>
    
    $ curl -v "http://localhost:8182/tasks/1.xml" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <task>
      <id>1</id>
      <status>FINISHED</status>
      <description>postgis import table postgis_multipoly into repository: file:/data/repos/7a4b140f-0703-43bc-942a-dfc60da9f3eb/.geogig/</description>
      <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/1.xml" type="application/xml"/>
      <result>
        <RevTree>
          <treeId>80c482d14c33fe0bf38ecc3ce2cd411a9222be48</treeId>
        </RevTree>
      </result>
    </task>
    
.. note::  Imported data will be on the working tree of the transaction.  To commit the data to the repository, use the :ref:`command_add` and :ref:`command_commit` commands before ending the transaction.