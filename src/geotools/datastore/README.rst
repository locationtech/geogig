######################################################
GeoGig GeoTools Extension
######################################################

This extension to GeoGig is meant to provide a means for importing and exporting
geospatial data to and from GeoGig into a variety of data stores.

GeoGig Details
=======

Project Lead: `Gabriel Roldan <https://github.com/groldan>`_

Source files use the following header::
   
/* Copyright (c) {creation date},{revision date} Boundless and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.phpl
 * 
 * Contributors:
 *  Gabriel Roldan (OpenPlans) - initial implementation
 *  Johnathan Garrett (LMN Solutions) - fix #43
 */

As indicated above the code is distributed under an `Eclipse Distribution License <LICENSE.txt>`_ license (which is a BSD 3 Clause License). The contributors section is optional, and is often used to thank those submitting pull requests.

Build
=====

The GeoGig GeoTools extension is built using Maven::
  
  cd src
  mvn clean install

Functional Tests
----------------

Functional tests require a PostGIS database that can be used for testing, and are run with the online profile::

  mvn -Ponline
  
The PostGIS database must be populated with the test tables from the .sql file::

  'src/test/resources/org/geogig/geotools/cli/test/functional/geogig_pg_test.sql'
  
When the functional tests are run for the first time, a database configuration file will be created at the
user root directory called '.geogig-pg-tests.properties'.  Open this file and enter the connection information
for the test database.

Additional Profiles
-------------------
    
Any additional build profiles are documented in the root `pom.xml`:pom.xml .

If you would like to work in Eclipse use of the `m2eclipse plugin <http://www.sonatype.org/m2eclipse>`_ recommended.

Please carefully apply the code formatting options in the buld/eclipse/formatter.xml file. These are the standard
Java formatting options with 100 character line length for both code and comments, and 4 spaces for indentation.
It is also recommended to use the code templates from build/eclipse/codetemlates.xml.

Participation
=============

The project is hosted on github:

* https://github.com/opengeo/GeoGig

Participation is encouraged using the github *fork* and *pull request* workflow::

* file headers are described above
* include test case demonstrating functionality
* contributions are expected to pass test and not break the build

Project resources:

* `Full on-line project documentation <http://opengeo.github.com/GeoGig>`_
* `GeoGig Discussion Group <https://groups.google.com/a/opengeo.org/group/geogig/>`_
* The build is `actively monitored using hudson <http://hudson.opengeo.org/hudson/view/geogig/>`_
* https://github.com/opengeo/GeoGig/issues

Additional resources:

* `guava-libraries <http://code.google.com/p/guava-libraries/>`_

