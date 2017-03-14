.. _start.installation:

Installation
============

This section describes the steps needed to install GeoGig.

Binaries
--------

Pre-built binaries are available for GeoGig.

#. If not already on your system, install a `Java JDK <http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_. GeoGig requires Java 8 or higher to run. Make sure the `java` executable is accessible (i.e. check your PATH environment variable) and the JAVA_HOME environment variable points to the JDK or JRE installation directory.

#. After the JDK is installed, navigate to http://geogig.org and click :guilabel:`Download`.

#. Extract this archive to your preferred program directory. (For example, :file:`C:\\Program Files\\GeoGig` or :file:`/opt/geogig`.)

   .. note:: The same packages can be used on Windows, OS X, and Linux.

#. Add the bin directory to your ``PATH`` environment variable.

When finished, you should be able to run the ``geogig --help`` and see the command usage.

Building from source code
-------------------------

To build GeoGig an executable binary that you can run:

#. Clone the GeoGig source code repository. To do so, create a new folder where you want the GeoGig source code to be kept, open a terminal and move to that folder, then type the following::

	   git clone https://github.com/locationtech/geogig.git

#. If not already on your system, install a `Java JDK`_. A Java JRE is not sufficient to build GeoGig.

#. If not already on your system, install `Maven <http://maven.apache.org/download.cgi>`_.

#. Move to the ``src/parent`` folder under the folder where you have cloned the GeoGig source code, and type the following::

	   mvn clean install

   .. note:: To speed up the build process, you can skip tests:

   ::

     mvn clean install -DskipTests

#. GeoGig will now build. Scripts ready to be run should be available in the :file:`src/cli-app/target/geogig/bin` directory. Add that directory to your ``PATH`` environment variable.

When finished, you should be able to run the ``geogig --help`` from a terminal and see the command usage.

Running on Windows
------------------

GeoGig uses `RocksDB <http://rocksdb.org/>`_ as the default storage backend.  On Windows machines, the libraries for RocksDB require the `Visual C++ Redistributable for Visual Studio 2015 <https://www.microsoft.com/en-us/download/details.aspx?id=48145>`_.  If you experience an ``UnsatisfiedLinkError`` exception when running GeoGig, make sure you have the above dependency installed on your system.

Only Windows 10 supports colored text using ANSI escape sequences. On previous versions of windows, ANSI support can be enabled by installing `ANSICON <http://adoxa.altervista.org/ansicon/>`_ and setting the ``ansi.enabled`` config parameter to ``true``. See the config section :ref:`repo.config`.

Installing ANSICON
==================

#. Download the `ANSICON <http://adoxa.altervista.org/ansicon/>`_  zip.

#. Unzip the file to it's own location, such as ``C:\Program Files\Ansicon\``

#. Add the ANSICON location to the Windows PATH, found under ``System -> Advanced System Properties -> Environment Variables``

#. Open a ``cmd`` or ``powershell`` terminal and type ``ansicon`` to confirm the PATH variable is set correctly. If the PATH is correct  information about the Windows version will be printed in the console. This command will enable ANSICON for this terminal session only.

.. code-block:: console

   ansicon
   Microsoft Windows [Version 6.3.9600]
   (c) 2013 Microsoft Corporation. All rights reserved.

#. To make ANSICON load automatically with new terminals type:

.. code-block:: console

   ansicon -i

#. ANSICON is now enabled by default in all terminals.

Uninstalling ANSICON
====================

#. To remove ANSICON from the terminal defaults type:

.. code-block:: console

   ansicon -u

#. Remove ANSICON from the windows ``PATH``

#. Delete the ANSICON folder from the location it was installed.
