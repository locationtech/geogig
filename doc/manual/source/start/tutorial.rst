.. _start.tutorial:

GeoGig tutorial
===============

GeoGig is a Distributed Version Control System (DVCS) for geospatial data.

This document is a short introduction to the main ideas and elements of GeoGig. It describes how to set up and use GeoGig to version spatial data, introducing the following operations:

* Importing unversioned spatial data into GeoGig
* Making changes and storing snapshots ("commit")
* Maintaining independent lines of modifications ("branch")
* Integrating changes from separate branches ("merge")
* Resolving conflicting edits
* Synchronizing data across a network ("push" and "pull")
* Marking specific versions of the data ("tag")
* Exporting data from GeoGig to a shapefile

This tutorial assumes no prior experience with GeoGig. More details can be found in later sections.

Installation
------------

Follow the instructions on the :ref:`start.installation` page to install GeoGig.

Configuration
-------------

Before we start working with geospatial data in GeoGig, you have to provide GeoGig with an user name and email, using the ``config`` command, substituting your name and email:

.. code-block:: console

   geogig config --global user.name "Author"
   geogig config --global user.email "author@example.com"

Initialization
--------------

First, we must create a new repository. Create a directory folder that will contain the repository, move into it, and initialize the GeoGig repository typing

.. code-block:: console

   geogig init

Now your GeoGig repository is ready to manage and version your geospatial data. Note that a :file:`.geogig` directory was created.

Sample data
-----------

:download:`Download the tutorial sample data <http://geogig.org/tutorial_data.zip>`

This archive contains multiple directories, named ``snapshot1`` through ``snapshot5``. Each directory contains a single shapefile, that all contain slight variations. For the purposes of this tutorial, each shapefile will be considered a "snapshot" of the data in time. We will use these snapshots to simulate the creation and editing of the data in GeoGig.

.. todo:: Perhaps more details about the differences between the five shapefiles.

Extract the archive into the repository directory created in the previous step.

Importing data
--------------

To work with data in GeoGig, it has to first be imported into the repository **working tree**.

We will start by importing the ``snapshot1/parks.shp`` shapefile, using the following command

.. code-block:: console

   geogig shp import snapshot1/parks.shp

The response will look like this:

.. code-block:: console

   Importing from shapefile snapshot1/parks.shp
  
   Importing parks            (1/1)...
   100%
   snapshot1/parks.shp imported successfully.

The data from the shapefile is now in the working tree. This means it is not versioned yet, but it is now in a format that GeoGig can understand, so it can be aware of the data and the changes you might introduce.

Run the following command to verify that your data is actually in the working tree:

.. code-block:: console

   geogig ls -r

The response will look like this:

.. code-block:: console

   Root tree/
           2
           1
           3

Features from the shapefile are added to the working tree under a tree named ``Root tree``. A **tree** in a GeoGig repository is analogous to a directory in a filesystem. Features are named as numbers, reflecting the order in which they are found in the source data. It is not necesarily the same order in which they are listed by the ``ls`` command.

.. todo:: Why the difference?

Running the ``status`` command will give you information about the data you have that is not already versioned.

.. code-block:: console

   geogig status

.. code-block:: console

   # On branch master
   # Changes not staged for commit:
   #   (use "geogig add <path/to/fid>..." to update what will be committed
   #   (use "geogig checkout -- <path/to/fid>..." to discard changes in working directory
   #
   #      added  parks
   #      added  parks/2
   #      added  parks/1
   #      added  parks/2
   # 4 total.

Adding data
-----------

To tell GeoGig that you want to version data in the working tree, you have to add it to the **staging area**. To do it, run the following command:

.. code-block:: console

   geogig add

The response will look like this:

.. code-block:: console

   Counting unstaged elements...4
   Staging changes...
   100%
   3 features and 1 trees staged for commit
   0 features and 0 trees not staged for commit

Now your data is ready to be used to create a snapshot (a **commit** in GeoGig terminology).

If you run the ``status`` command again, you will see a different output, since your data has now been added and is ready to be versioned. 

.. code-block:: console

   geogig status

The response will look like this:

.. code-block:: console

   # On branch master
   # Changes to be committed:
   #   (use "geogig reset HEAD <path/to/fid>..." to unstage)
   #
   #      added  parks
   #      added  parks/2
   #      added  parks/1
   #      added  parks/3
   # 4 total.

The staging area is the last area before the data gets versioned in the repository database.

Committing
----------

Committing means to create a new version of the data which is in the staging area.

Type the following command:

.. code-block:: console

   geogig commit -m "first version"

The response will look like this:

.. code-block:: console

   100%
   [592006f6b541557a203279be7b4a127fb9dbb2d9] first version
   Committed, counting objects...3 features added, 0 changed, 0 deleted.

The text between quotes after the ``-m`` option is the commit message, which describes the snapshot in a human-readable format.

Making edits
------------

To create a new version, follow the same procedure as before: import new data, add it, and then commit. All editing of data must be done externally to GeoGig. We'll see this next.

The :file:`snapshot2/parks.shp` file contains the same data as the first file, but with an extra feature. Import this file.

.. code-block:: console

  geogig shp import snapshot2/parks.shp

If you run the ``status`` command after importing (and before adding), you will see it reports one added element. GeoGig will only report modifications to features that have been changed.

.. code-block:: console

   geogig status

The response will look like this:

.. code-block:: console

   # On branch master
   # Changes not staged for commit:
   #   (use "geogig add <path/to/fid>..." to update what will be committed
   #   (use "geogig checkout -- <path/to/fid>..." to discard changes in working directory
   #
   #      added  parks/4
   # 1 total.

Now add the new feature:

.. code-block:: console

   geogig add

.. code-block:: console

   Counting unstaged elements...1
   Staging changes...
   100%
   1 features and 0 trees staged for commit
   0 features and 0 trees not staged for commit

Then commit to create a new version:

.. code-block:: console

   geogig commit -m "first modification"

.. code-block:: console

   100%
   [7b6e36db759da8d09b5b1bb726009b3d2c5ca5f7] first modification
   Committed, counting objects...1 features added, 0 changed, 0 deleted.

Viewing repository history
--------------------------

You can use the ``log`` command to see the history of your repository. The history consists of the listing of commits, ordered in reverse chronological order (most recent first).

.. code-block:: console

   geogig log

.. code-block:: console

   Commit:  7b6e36db759da8d09b5b1bb726009b3d2c5ca5f7
   Author:  Author <author@example.com>
   Date:    (19 minutes ago) 2013-04-11 15:24:10 +0300
   Subject: first modification

   Commit:  592006f6b541557a203279be7b4a127fb9dbb2d9
   Author:  Author <author@example.com>
   Date:    (25 minutes ago) 2013-04-11 15:18:14 +0300
   Subject: first version

Creating a branch
-----------------

Data editing can be done on multiple history lines of the repository, so one line can be kept clean and stable while edits are performed on another line. These lines are known as **branches**. You can merge commits from one branch to another branch at any time.

To create a new branch named "myedits", run the following command:

.. code-block:: console

   geogig branch myedits -c

The response will look like this:

.. code-block:: console

   Created branch refs/heads/myedits

The ``-c`` option tells GeoGig to not only create the branch, but also switch the repository to be working on that branch. Everything done now will be added to this new history line.

.. note:: The default branch is named ``master``.

Now use the :file:`snapshot3/parks.shp` file to create a new snapshot (once again, import it, add it and then commit it). It contains the same data of the last version, but with another new feature. 

.. code-block:: console

   geogig shp import snapshot3/parks.shp
   geogig add
   geogig commit -m "added new feature"

The ``log`` command will show a history like this:

.. code-block:: console

   Commit:  c04d0a968696744bdc32bf865f9675a2e55bf447
   Author:  Author <author@example.com>
   Date:    (26 minutes ago) 2013-04-11 15:27:15 +0300
   Subject: added new feature

   Commit:  7b6e36db759da8d09b5b1bb726009b3d2c5ca5f7
   Author:  Author <author@example.com>
   Date:    (29 minutes ago) 2013-04-11 15:24:10 +0300
   Subject: first modification

   Commit:  592006f6b541557a203279be7b4a127fb9dbb2d9
   Author:  Author <author@example.com>
   Date:    (35 minutes ago) 2013-04-11 15:18:14 +0300
   Subject: first version

Merging commits from a branch
-----------------------------

Our repository has now two branches: the one we have created (``myedits``) and the main history branch (``master``).

Let's merge the changes we have just added from the ``myedits`` branch into the ``master`` branch.

First **move to the branch where you want the changes to go**, in this case ``master``. The ``checkout`` command, followed by the name of the branch, will switch to this branch.

.. code-block:: console

   geogig checkout master

The response will look like this:

.. code-block:: console

   Switched to branch 'master'

The ``log`` command will show the following history. Use the ``--oneline`` option to compact the output:

.. code-block:: console
 
   geogig log --oneline

The response will look like this:

.. code-block:: console

   7b6e36db759da8d09b5b1bb726009b3d2c5ca5f7 first modification
   592006f6b541557a203279be7b4a127fb9dbb2d9 first version

Notice that the most recent commit (with the message "added new feature") is missing. This is because it was added to the ``myedits`` branch, not the ``master`` branch.

To merge the work done in the ``myedits`` branch into the current ``master`` branch, enter the following command:

.. code-block:: console

   geogig merge myedits

The response will look like this:

.. code-block:: console

   100%
   [c04d0a968696744bdc32bf865f9675a2e55bf447] added new feature
   Committed, counting objects...1 features added, 0 changed, 0 deleted.

Now the commit introduced in the ``myedits`` branch is now present in ``master``, as the ``log`` operation will display.

.. code-block:: console

   geogig log --oneline

.. code-block:: console

   c04d0a968696744bdc32bf865f9675a2e55bf447 added new feature
   7b6e36db759da8d09b5b1bb726009b3d2c5ca5f7 first modification
   592006f6b541557a203279be7b4a127fb9dbb2d9 first version

Handling merge conflicts
------------------------

We just saw that the work done on one branch could be merged to another branch without problems. This is not always possible.

To see this in action, create a new branch named ``fix``, and create a commit based in the ``snapshot4/parks.shp`` shapefile.

.. code-block:: console

   geogig branch fix -c
   geogig shp import snapshot4/parks.shp
   geogig add
   geogig commit -m "fix branch edits"

This new shapefile changes a geometry, and updates the corresponding area field to reflect that change.

Now go back to the ``master`` branch and create a new commit with the data in :file:`snapshot5/parks.data`. This is the same data as ``snapshot3/parks.data``, but it changes the units in the area field.

.. code-block:: console

   geogig checkout master
   geogig shp import snapshot5/parks.shp
   geogig add
   geogig commit -m "master branch edits"

This is a conflict situation, as the same data has been changed differently in two branches. If you try to merge the ``fix`` branch into ``master``, GeoGig cannot automatically resolve this situation and so will fail.

.. code-block:: console

   geogig merge fix

.. code-block:: console

   100%
   CONFLICT: Merge conflict in parks/5
   Automatic merge failed. Fix conflicts and then commit the result.

You can see that there is a conflict by running the ``status`` command:

.. code-block:: console

   geogig status

.. code-block:: console

   # On branch master
   #
   # Unmerged paths:
   #   (use "geogig add/rm <path/to/fid>..." as appropriate to mark resolution
   #
   #      unmerged  parks/5
   # 1 total.

An **unmerged path** represents a element with a conflict.

You can get more details about the conflict by running the ``conflicts`` command:

.. code-block:: console

   geogig conflicts --diff

The response will look like this (edited for brevity):

.. code-block:: console

   ---parks/5---
   Ours
   area: 15297.503295898438 -> 164594.90384123762
   the_geom: MultiPolygon -122.8559991285487,42.3325881068491 ...

   Theirs
   area: 15297.503295898438 -> 15246.59765625
   the_geom: MultiPolygon -122.8559991285487,42.3325881068491 ...

The output indicates that the value in the ``area`` attribute of the ``parks.5`` feature is causing the conflict.

The conflict has to be solved manually. You will have to merge both versions yourself, or just select one of the versions to be used.

.. todo:: Once we have a UI, show a manual merge using the UI.

Assume we want to use the changed feature in the ``fix`` branch. Since we are in the ``master`` branch, the ``fix`` branch is considered "theirs." Run the following command:

.. code-block:: console

   geogig checkout -p parks/5 --theirs

The response will look like this:

.. code-block:: console

   Objects in the working tree were updated to the specifed version.

That puts the ``fix`` branch version in the working tree, overwriting what was there. Add this to remove the conflict.

.. code-block:: console

   geogig add

.. code-block:: console

   Counting unstaged elements...1
   Staging changes...
   100%
   1 features and 0 trees staged for commit
   0 features and 0 trees not staged for commit

Now that the conflict has been resolved, you may commit the change. There is no need to add a commit message, since that is created automatically during a merge operation.

.. code-block:: console

   geogig commit


Tagging a version
-----------------

You can add a "tag" to a version to easily identify a snapshot with something more descriptive than the identifier associated with each commit.

To do so, use the ``tag`` command:

.. code-block:: console

   geogig tag -m "First official version"

Now you can refer to the current version with that name.

.. todo:: Example?

Exporting from a GeoGig repository
----------------------------------

Data can be exported from a GeoGig repository into several formats, ready to be used by external applications.

To export a given tree to a shapefile, use the ``shp export`` command.

.. code-block:: console

   geogig shp export parks parks.shp

.. code-block:: console

   Exporting parks...
   100%
   parks exported successfully to parks.shp

That will create a file named ``parks.shp`` in the current directory that contains the current state of the repository.

Past/other versions can be exported by prefixing the tree name with a commit ID and a colon, as in the following example:

.. code-block:: console

   geogig shp export c04d0a968696744bdc32bf865f9675a2e55bf447:parks parks.shp

Use "HEAD" notation to export changes relative to the current working revision. For example, ``HEAD~1`` refers to the second-most recent commit, ``HEAD~2`` refers to the commit prior to that, etc.

.. code-block:: console

   geogig shp export HEAD~1:parks parks.shp

Synchronizing repositories
--------------------------

A GeoGig repository can interact with other GeoGig repositories that are working with the same data. Other GeoGig repositories are know as **remotes**.

In our situation, we created a new repository from scratch using the ``init`` command. But if we wanted to start with a copy of an existing repository (referred to as the ``origin``), use the ``clone`` command.

Let's clone the repository we have been working on. Create a new directory in your file system, move into it and run the following command (replacing the path with the location of the original GeoGig repository):

.. code-block:: console

   mkdir /path/to/newrepo
   cd /path/to/newrepo
   geogig clone /path/to/origrepo

The response will look like this:

.. code-block:: console

   Cloning into 'newrepo'...
   100%
   Done.

With the repository cloned, you can work here as you would normally do, and the changes will be placed on top of the changes that already exist from the original repository.

You can merge commits from the ``origin`` repository to this new repository by using the ``pull`` command. This will update the current branch with changes that have been made on that branch in the remote repository since the last time both repositories were synchronized.

.. code-block:: console

   geogig pull origin

To move your local changes from your repository into ``origin``, use the ``push`` command:

.. code-block:: console

   geogig push origin

Tutorial complete
-----------------

This tour has given you the basics of managing data with GeoGig. Read on to the rest of the GeoGig Manual to learn more.
