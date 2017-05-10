.. _init:

Initializing a repository
=========================

.. note:: This page is about creating a new repository. For creating a copy of an already-existing repository, see Cloning.

.. todo:: Add link to cloning.

The first step, when starting out versioning data, is to initialize a new GeoGig repository. A repository keeps your data and all the different versions of it, so you can go revert to any given version at any time.

GeoGig is a command-line application, and it is run from the console typing commands in the form of:

.. code-block:: console

   geogig <command> <options>

To create a new repository, the ``init`` command is used.

.. code-block:: console

   geogig init

This command will create a new directory called ``.geogig`` in the current directory, which will contain all your data, both the current version and the entire history of all older versions. Other than that, GeoGig will put nothing else in that directory.

.. note:: In GeoGig, the working tree is not the folder where the ``.geogig`` subfolder is located. The working tree itself is located within the ``.geogig`` folder. This is a difference from ``git``, where the working tree is the directory where the ``.git`` directory is located.

All GeoGig commands have to be run from within a valid GeoGig repository. The only exception to this rule is the ``init`` command itself. In case you have several repositories on your system, the operations you execute will affect just the repository in the current working directory from where the commands are invoked.

The GeoGig repository created is empty. Even if the folder where the repository has been created previously contained some files, those are not considered part of the repository, and will be ignored, unless files in the directory are later imported into the working tree.

.. todo:: Link to full details of command.
