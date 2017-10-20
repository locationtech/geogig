GeoPackage Import/Export with Interchange Format
================================================

Overview
--------

GeoGig utilizes GeoTools libraries to export feature data to a GeoPackage.  A GeoPackage is an open format for transferring geospatial information via an SQLite database.  Because a GeoPackage is backed by a SQLite database, GeoGig is able to create additional tables and triggers to keep track of edits and facilitate disconnected editing.


GeoGig Interchange Format
-------------------------

In order to better support import and export functionality for GeoPackages, GeoGig provides an option to augment a GeoPackage with several tables that assist in keeping track of where data came from, and what changed.

Tables
......

:Table:
    geogig_metadata
:Description:
    This table simply contains the repository URI where the data came from originally.  This is not currently being used by GeoGig for any functionality.

*Example*:

.. table::

   +----------------------------------+
   | repository_uri                   |
   +==================================+
   | file:/Users/test/myRepo/.geogig/ |
   +----------------------------------+
   
-------

:Table:
    geogig_audited_tables
:Description:
    This table keeps track of all audited tables that have been exported into this GeoPackage.  It lists the table name as it exists in the GeoPackage (table_name), the tree path of the feature type within GeoGig (mapped_path), the audit table name within the GeoPackage (audit_table), and the commit id that the data was exported from (commit_id).

**Example**:

.. table::

   +------------+-------------+----------------+---------------+
   | table_name | mapped_path | audit_table    | commit_id     |
   +============+=============+================+===============+
   | myPoints   | my_points   | myPoints_audit | 23cf04ef62... |
   +------------+-------------+----------------+---------------+
 
-------

:Table:
    <featuretype> (i.e. myPoints)
:Description:
    Not specific to GeoGig, this is the GeoPackage table where the raw feature data is stored.  It contains a column with the id of the feature, as well as columns for every attribute of that feature.

*Example*:

.. table::

   +-----+-------------+--------------+---------+
   | fid | geom        | strAttr      | numAttr |
   +=====+=============+==============+=========+
   | 1   | POINT (1 1) | First Point  | 93      |
   +-----+-------------+--------------+---------+
   | 2   | POINT (5 5) | Second Point | 3442    |
   +-----+-------------+--------------+---------+
 
-------

:Table:
    <featuretype>_audit (i.e. myPoints_audit)
:Description:
    This table contains a log of all the changes made in the GeoPackage.  This table is populated by several triggers that are attached to the <featuretype> table.  It mirrors the columns found in the <featuretype> table in order to have a record of what exactly changed, but also adds a column that contains the timestamp of the change (audit_timestamp) and another column that declares the type of change that took place (audit_op).  An audit_op with a value of 1 represents an inserted feature, 2 represents an updated feature, and 3 represents a deleted feature.

*Example*:

.. table::

   +-----+-------------+--------------+---------+---------------------+----------+
   | fid | geom        | strAttr      | numAttr | audit_timestamp     | audit_op |
   +=====+=============+==============+=========+=====================+==========+
   | 1   | POINT (1 2) | First Point  | 93      | 2017-07-13 05:09:29 | 2        |
   +-----+-------------+--------------+---------+---------------------+----------+
   | 2   | POINT (5 5) | Second Point | 3442    | 2017-07-13 05:11:16 | 3        |
   +-----+-------------+--------------+---------+---------------------+----------+

This audit table shows that the first feature was moved from (1,1) to (1,2) while the second feature was removed from the dataset.

-------

:Table:
    <featuretype>_fids (i.e. myPoints_fids)
:Description:
    This table contains a mapping of all the feature ids in the GeoPackage to the feature ids of those same features within GeoGig.  This is important because when features are exported to a GeoPackage, they may have a different type name and the feature ids may be different.  GeoGig supports strings for feature ids while the feature ids in a GeoPackage will be integers.  This mapping makes it possible to accurately import the changes back into GeoGig.

*Example*:

.. table::

   +----------+------------+
   | gpkg_fid | GeoGig_fid |
   +==========+============+
   | 1        | fid_324abf |
   +----------+------------+
   | 2        | fid_c7d31c |
   +----------+------------+
 
Triggers
........

In addition to the above tables, GeoGig will create 3 triggers on the <featuretype> table.

:Trigger:
    <featuretype>_insert
:Description:
    When a row is inserted into <featuretype>, insert the row into <featuretype>_audit with an added timestamp and an audit_op of 1.

:Trigger:
    <featuretype>_update
:Description:
    When a row is updated in <featuretype>, insert that row into <featuretype>_audit with an added timestamp and an audit_op of 2.

:Trigger:
    <featuretype>_delete
:Description:
    When a row is deleted from <featuretype>, insert that row into <featuretype>_audit with an added timestamp and an audit_op of 3.

CLI Commands
------------

:Command:
    export
:Description:
    Exports a repository snapshot or a subset of it as a GeoPackage file.

*Parameters*:

.. table::

   +-------------+---------+------------------------------------------------------------------------------------------+
   | Parameter   | Type    | Description                                                                              |
   +=============+=========+==========================================================================================+
   | database    | String  | The GeoPackage file to export to.                                                        |
   +-------------+---------+------------------------------------------------------------------------------------------+
   | interchange | Boolean | If true, GeoGig will create all of the audit tables specified by the interchange format. |
   +-------------+---------+------------------------------------------------------------------------------------------+

-------

:Command:
    import
:Description:
    Imports a GeoPackage into the repository without using the interchange format.

*Parameters*:

.. table::

   +-----------+--------+--------------------------------+
   | Parameter | Type   | Description                    |
   +===========+========+================================+
   | database  | String | The GeoPackage file to import. |
   +-----------+--------+--------------------------------+
.. note::

   This works exactly like the shapefile and PostGIS import, utilising the GeoTools library.

-------

:Command:
    pull
:Description:
    Imports the changes made to a GeoPackage that was exported with the interchange format.
    
*Parameters*:

.. table::

   +-----------+--------+------------------------------------------------------------------------+
   | Parameter | Type   | Description                                                            |
   +===========+========+========================================================================+
   | database  | String | The GeoPackage file to import.                                         |
   +-----------+--------+------------------------------------------------------------------------+
   | table     | String | Feature table to import.,Required if tables are from multiple commits. |
   +-----------+--------+------------------------------------------------------------------------+
   | message   | String | Commit message to use for the import.                                  |
   +-----------+--------+------------------------------------------------------------------------+
.. note::

   This operation works by walking through all of the changes in the <featuretype>_audit table and building a new tree starting with the commit that the data was originally exported from.  Once the new tree is created, a commit will be created with the provided commit message and merged into the current branch.  It is possible that merge conflicts can occur during this operation.  They may be resolved the same way other merge conflicts are resolved.

-------

:Command:
    describe
:Description:
    Describes a feature table in the GeoPackage.

*Parameters*:

.. table::

   +-----------+--------+-------------------------------------------------+
   | Parameter | Type   | Description                                     |
   +===========+========+=================================================+
   | database  | String | The GeoPackage file with the table to describe. |
   +-----------+--------+-------------------------------------------------+
.. note::

   This works just like the shapefile and PostGIS describe functions.

-------

:Command:
    list
:Description:
    Lists all of the feature types in a GeoPackage.

*Parameters*:

.. table::

   +-----------+--------+----------------------------------------------+
   | Parameter | Type   | Description                                  |
   +===========+========+==============================================+
   | database  | String | The GeoPackage file with the tables to list. |
   +-----------+--------+----------------------------------------------+
.. note::

   This works just like the shapefile and PostGIS list functions.
   
Web API
-------

The Web API provides most of the above functionality, except describe and list, which are not applicable.  The GeoGig manual contains a full description of how to use these endpoints and what their outputs are in the “GeoPackage import and export Web-API” section.  

Export Diff
...........

In addition to the above functionality, the Web API adds an additional command called export-diff.  This works similarly to the standard export function, but instead of exporting a full snapshot, it only exports the features that have changed between two commits, as well as a full change log of those commits.  When the export-diff endpoint is used, GeoGig creates the following table inside the GeoPackage.

:Table:
    <featuretype>_changes (i.e. myPoints_changes)
:Description:
    This table contains a list of all the changes made in GeoGig between the two commits being exported.   It contains the GeoGig feature id, as well as a column that informs the type of change that the feature underwent.  An audit_op with a value of 1 represents an inserted feature, 2 represents an updated feature, and 3 represents a deleted feature.

*Example*:

.. table::

   +------------+----------+
   | GeoGig_fid | audit_op |
   +============+==========+
   | fid_324abf | 2        |
   +------------+----------+
   | fid_89fd1c | 1        |
   +------------+----------+

The <featuretype> table of this GeoPackage would only contain the two features involved in the diff.  This feature can be utilized by a client application to intelligently combine these changes with a larger already-existing GeoPackage.
