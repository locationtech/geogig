GeoGig 1.0-RC3 Release Notes
============================

August 4, 2016.

These are the release notes for the third GeoGig release candidate.

We have worked hard since RC2 on fixing the last outstanding bugs that prevented 
a solid release, and focused on some performance improvements.

This release is also the official launch of the PostgreSQL storage backend [1],
which allows to store several repositories in a single postgres database.

Also, the GeoServer plugin is being released for GeoServer 2.8, 2.9, and 2.10 series,
with several improvements both on GUI [2] and REST [3] configuration.

[1] <http://geogig.org/docs/repo/storage.html>
[2] <http://geogig.org/docs/interaction/geoserver_ui.html>
[3] <http://geogig.org/docs/interaction/geoserver_web-api.html>

The next steps are a strong focus on performance and scalability and a final
1.0 release by the end of the year. Stay tuned and write us back with your 
feedback about this important milestone.

The GeoGig team.-

Important features
------------------

- PostgresSQL storage backend
- GeoServer 2.8, 2.9, and 2.10 plugin
- GeoServer support for PostgreSQL repositories
- Several GeoServer GUI and REST improvements and user docs

Improvements
------------
- Update Postgres backend to use a serial integer as repository primary key 
- Move core API to a new geogig-api module 
- Allow amend to only update commit message. 
- Dramatic improvement for mergeop when merging features. 


Most Important Bug fixes
------------------------

- IMPORTANT BUG EXPOSED: leaf RevTree node storage order is wrong
- Geometry automerge is dangerous and produces invalid geometries
- Unwanted geometry changes on re-import of polygon layers bug enhancement
- Immutability of RevFeature compromised
- Enforce normalization of Polygon geometries
- Update Diff traversals to close properly when terminated early
- `reset --hard` and `merge --abort` leave dangling blobs
- `add` command deletes all conflicts
- GeoGigFeatureStore removes feature tree if instructed to delete all features in a layer

