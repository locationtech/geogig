######################################################
GeoGig - Geospatial Distributed Version Control System
######################################################

.. image:: https://travis-ci.org/boundlessgeo/GeoGig.png?branch=master
   :target: https://travis-ci.org/boundlessgeo/GeoGig

Welcome to the GeoGig project, exploring the use of distributed management of spatial data. GeoGig draws inspiration from `Git <http://git-scm.com/>`_, but adapts its core concepts to handle versioning of geospatial data. Users are able to import raw geospatial data (currently from Shapefiles, PostGIS or SpatiaLite) in to a repository where every change to the data is tracked. These changes can be viewed in a history, reverted to older versions, branched in to sandboxed areas, merged back in, and pushed to remote repositories. GeoGig is written in Java, available under the BSD License.

For background reading see these two papers on the spatial distributed versioning `Concept <http://boundlessgeo.com/whitepaper/new-approach-working-geospatial-data-part-1/>`_, 
`Implementation <http://boundlessgeo.com/whitepaper/distributed-versioning-geospatial-data-part-2//>`_, and `Potential Development <http://boundlessgeo.com/whitepaper/distributed-versioning-geospatial-data-part-3/>`_.

Details
=======

Project Lead: `Gabriel Roldan <https://github.com/groldan>`_

Status: A 1.0-beta1 version has been released and it is available for download, with a full commandline interface to import data and work with repositories.

License
=======

GeoGig is proudly open source:

* Source code is distributed under an `Eclipse Distribution License <LICENSE.txt>`_ license (which is a BSD 3 Clause License) unless otherwise stated.
* Varint: is from the Apache Mahout project and is distributed under the `Apache License Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0>`_ .
* DiffMatchPath: is from Neil Fraser and is distributed under the `Apache License Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0>`_ .
* XMLReader: is from osmosis and has been released into the public domain

For details review `About This Content<about.html>`_ and the `Eclipse Foundation Software User Agreement<notice.html>`_.

Download
=========

No release downloads available yet. Keep tuned for the first 1.0 release candidate soon.
.. `Version 1.0-beta1 <http://sourceforge.net/projects/geogig/files/geogig-1.0-beta1/geogig-cli-app-1.0-beta1.zip/download>`_ from SourceForge. Documentation available for `download <http://sourceforge.net/projects/geogig/files/geogig-1.0-beta1/geogig-user-mannual-1.0-beta1.zip/download>`_ and `online <http://geogig.org/docs/index.html>`_.

Installation
============

Unzip the geogig-cli-app-1.0-beta1.zip to an applications directory, and then add the unzipped geogig/bin/ folder to your PATH.

Running
=======

See the `QuickStart <http://geogig.org/#install>`_ guide for an introduction to GeoGig. Additional information available at the full `Manual <http://geogig.org/docs/index.html>`_.

Developing
===========

If you want to get involved in the development of GeoGig, build GeoGig yourself or know more about the technical details behind GeoGig, check the `developers section <https://github.com/locationtech/geogig/blob/master/doc/technical/source/developers.rst>`_.

Participation
=============

Everyone is invited to participate in GeoGig and help in its development. Check the `How to help <https://github.com/locationtech/geogig/master/helping.rst>`_ section to read about how you can help us improve GeoGig.
