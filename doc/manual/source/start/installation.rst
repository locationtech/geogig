.. _start.installation:

Installation
============

This section describes the steps needed to install GeoGig.

Pre-requisites
--------------

GeoGig requires a Java 8 runtime environment. If not already on your system, install a `Java JDK <http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_. Make sure the `java` executable is accessible (i.e. check your PATH environment variable) and the JAVA_HOME environment variable points to the JDK or JRE installation directory.


Command line executable
-----------------------

Pre-built binaries are available for GeoGig.

#. After the JDK is installed, navigate to the project's releases page https://github.com/locationtech/geogig/releases/ and download the latest release. For example, `geogig-1.1.0.zip`.

#. Extract this archive to your preferred program directory. (For example, :file:`C:\\Program Files\\GeoGig` or :file:`/opt/geogig`.)

   .. note:: The same packages can be used on Windows, OS X, and Linux.
   
   .. _pathsetup:
   
#. Add the `bin` directory to your ``PATH`` environment variable.

   - OS X

      1. Open ``Terminal`` from the Utilities folder in the Applications directory.
   
      2. Open the bash profile with nano.

      .. code-block:: console

         $ nano ~/.bash_profile
   
      3. Specify the java home directory inside the profile by adding the following line to the end of the file.

      .. code-block:: console

         export JAVA_HOME=$(/usr/libexec/java_home)
   
      4. Add the bin directory from your GeoGig install location to the ``PATH`` by adding the following line at the end of the file.  In this case, GeoGig was extracted to :file:`/opt/geogig`.

      .. code-block:: console

         export PATH=$PATH:/opt/geogig/bin
   
      5. Save the bash profile with Ctrl+O, and exit using Ctrl+X.

      6. Reload the profile.

      .. code-block:: console

         $ source ~/.bash_profile
   
      7. Verify GeoGig functionality.

      .. code-block:: console

         $ geogig --help
         usage: geogig [--repo <URI>] <command> [<args>]

         The most commonly used geogig commands are:
         ...

   - Windows
   
      1. Open up ``System`` from the Windows control panel and click on ``Advanced system settings``.  Depending on how your control panel is displayed, this may be under the ``System and Security`` section.
      
      2. Click on ``Environment Variables``.
      
      3. Look through the ``System Variables`` section and find the ``PATH`` (or ``Path``) variable and click ``Edit...``.  If it does not exist, click ``New...``
      
      4. Add the bin directory from your GeoGig install location by adding the following path to the end of the existing value, separated by a semicolon.  In this case, GeoGig was extracted to :file:`C:\\Program Files\\GeoGig`.
      
      .. code-block:: console

         C:\Program Files\GeoGig\bin
         
      .. note:: In newer versions of windows, the ``Path`` variable will be displayed as a list.  In this case, simply add a new entry for the above path.
      
      5. Click ``OK`` through each window to save the changes.
      
      6. Verify GeoGig functionality in a new Command Prompt window.

      .. code-block:: console

         > geogig --help
         usage: geogig [--repo <URI>] <command> [<args>]

         The most commonly used geogig commands are:
         ...

When finished, you should be able to run the ``geogig --help`` and see the command usage.

GeoServer plug-in
-----------------

A GeoServer extension is available to allow GeoServer to interact with a GeoGig repository and use it as a datastore. It enables a GeoGig repository to be exposed as a remote for cloning, pushing, and pulling, as well as to publish its data via OGC services.

The GeoServer GeoGig plug-in binaries are pre-compiled and ready to be downloaded for the most recent versions of GeoServer.
Refer to the project's releases page https://github.com/locationtech/geogig/releases/ and download the latest release. For example, `GeoServer 2.11.x plugin <https://github.com/locationtech/geogig/releases/download/v1.1.0/geoserver-2.11-SNAPSHOT-geogig-plugin.zip>`_.

Once downloaded, unzip the contensts of the ``geoserver-<version>-geogig-plugin.zip`` file inside GeoServer's ``WEB-INF/lib`` directory and restart GeoServer.

For information on how to configure GeoGig on GeoServer, refer to the :ref:`geoserver_ui` and :ref:`geoserver_web-api` sections.

PostgreSQL JDBC Driver version
++++++++++++++++++++++++++++++

GeoServer versions lower than 2.12 come with an older version of the PostgreSQL JDBC driver than the one required by GeoGig.
GeoGig requires version ``42.1.1`` (included in the geogig plugin zip file as ``postgresql-42.1.1.jar``), while GeoServer comes
with version ``9.4.1211``.

GeoGig needs the above mentioned version or higher in order to be able to transferring data to and from the postgres database in pure binary form.

Given the way servlet containers (such as Apache Tomcat or Jetty) work, if the two jar files end up being in GeoSevrer's ``WEB-INF/lib`` folder,
one or the other may be loaded first, in a non deterministic way. Hence **you'll need to remove the older jar file** from GeoSevrer's ``WEB-INF/lib`` folder
before restarting GeoServer and after installing the GeoGig plugin as described above.

Note GeoGig will verify the correct driver version is in use and won't work if the old driver is still installed and loaded.

Building from source code
-------------------------

If you're interested in building GeoGig yourself, go to the project's source code repository and follow the instructions there: https://github.com/locationtech/geogig

Running on Windows
------------------

GeoGig for Windows is only available for Windows 64-bit versions.

GeoGig uses `RocksDB <http://rocksdb.org/>`_ as the default storage backend and for some temporary storage needs.  On Windows machines, the libraries for RocksDB require the `Visual C++ Redistributable for Visual Studio 2015 <https://www.microsoft.com/en-us/download/details.aspx?id=48145>`_.  If you experience an ``UnsatisfiedLinkError`` exception when running GeoGig, make sure you have the above dependency installed on your system.

Only Windows 10 supports colored text using ANSI escape sequences. On previous versions of windows, ANSI support can be enabled by installing `ANSICON <http://adoxa.altervista.org/ansicon/>`_ and setting the ``ansi.enabled`` config parameter to ``true``. See the config section :ref:`repo.config`.

Installing ANSICON
++++++++++++++++++

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
++++++++++++++++++++

#. To remove ANSICON from the terminal defaults type:

.. code-block:: console

   ansicon -u

#. Remove ANSICON from the windows ``PATH``

#. Delete the ANSICON folder from the location it was installed.
