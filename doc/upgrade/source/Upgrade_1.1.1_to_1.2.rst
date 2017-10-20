Upgrading to GeoGig 1.2.0
=========================

.. warning:: Ensure you have your repository database fully backed up before moving to GeoGig 1.2.0.

.. warning:: GeoGig 1.2.0 is only compatible with GeoServer 2.12.  If you are using GeoServer 2.11 or earlier, use GeoGig 1.1.1.


Important Change in GeoGig 1.2.0
--------------------------------

One of the big changes in GeoServer 2.12 was to replace Restlet with Spring MVC. This necessitated GeoGig to also replace Restlet with Spring MVC. We made the new web API exactly equivalent to the old web API, so all the requests and responses are exactly the same - web client applications should not need any changes. We also made some improvements to the `API documentation <http://geogig.org/docs/interaction/web-api.html>`_.

This, however, means that GeoGig 1.2.0 is only compatible with GeoServer 2.12 - it is **not** compatible with GeoServer 2.11 (or earlier).  If you are using GeoServer 2.11 or earlier, you *must* use GeoGig 1.1.1.  If you are using GeoServer 2.12 or later, you *must* use GeoGig 1.2.0.

Upgrading to GeoGig 1.2.0
-------------------------

GeoGig 1.2.0 doesn't change any underlying database formats - you should be able to do an in-place upgrade of GeoGig.  However, we *strongly* recommend first validating a test system before attempting to upgrade a production server.
Here is a simple process to do this;


#.  Backup your existing databases (use existing PostgreSQL tools)
#.  Create a new PostgreSQL database
#.  Use GeoGig 1.2.0 to `clone` the existing repository into the new database
    ::

       geogig clone "postgresql://OLDdatabase/<repo>" "postgresql://NEWdatabase/<repo>"

#.  Create QuadTree indexes in the new repository for the branches/layer required (if necessary)
#.  Upgrade your **test** server to GeoGig 1.2.0, pointing to the new database
#.  Test until you are satisfied that GeoGig is working in your environment and the datasets are working as expected
#.  Repeat steps 2-4 to set up a new production database
#.  Upgrade your production server to GeoGig 1.2.0, pointing to the new production database


Other Changes
-------------

Please see the `release notes <https://github.com/locationtech/geogig/releases/tag/v1.2.0>`_.
