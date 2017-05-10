.. _start.tutorial:

GeoGig tutorial
===============

GeoGig is a Distributed Version Control System (DVCS) for geospatial data.

This document is a short introduction to the main ideas and elements of GeoGig. It describes how to set up and use GeoGig to version spatial data, introducing the following operations:

* Importing unversioned spatial data into GeoGig ("import")
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

Data
----

The data used in the following examples was supplied by the City of Raleigh, and is available for download from the `City of Raleigh Open Data Portal. <https://data.raleighnc.gov/>`_

For the purposes of this tutorial `download the sample data HERE. <https://s3.amazonaws.com/geogig-tutorial-data/geogig_tutorial_data.zip>`_

This zip file contains point locations of parks maintained by the City of Raleigh, named ``Raleigh_Park_Locations``. Each version of the data has already been slightly modified in order to simulate an example workflow.

.. image:: img/parks_layer.png
      :scale: 75

Configuration
-------------

Before we start working with geospatial data in GeoGig, you must provide GeoGig with a name and email, using the ``config`` command, substituting your name and email:

.. code-block:: console

   geogig config --global user.name "Author"
   geogig config --global user.email "author@example.com"

Initialization
--------------

We must create a new repository. Create a new folder to contain the repository and extract the data zip into that directory. Initialize the GeoGig repository by typing:

.. code-block:: console

   geogig init

Now your GeoGig repository is ready to manage and version your geospatial data. Note that a :file:`.geogig` directory was created.

Importing data
--------------

To work with data in GeoGig, it has to first be imported into the repository **working tree**.

We will start by importing the ``Raleigh Park Locations`` shapefile using the following command:

.. code-block:: console

   geogig shp import parks/Raleigh_Park_Locations.shp

The response will look like this:

.. code-block:: console

   Importing from shapefile parks/Raleigh_Park_Locations.shp
   0%
   Importing Raleigh_Park_... (1/1)...
   0%
   113 features inserted in 57.11 ms

   Building final tree Raleigh_Park_Locations...

   113 features tree built in 3.450 ms
   100%
   parks/Raleigh_Park_Locations.shp imported successfully.

The data from the shapefile is now in the working tree, but it is not yet versioned. However, the data is now in a format that GeoGig can understand, such that it may be aware of any changes to the data you might introduce.

Run the following command to verify that your data is in the working tree.

.. code-block:: console

   geogig ls -r

The response will look like this:

.. code-block:: console

   Root tree/
           9
           7
           8
           ...
           11
           12

Features from the shapefile are added to the working tree under a tree named ``Root tree``. A **tree** in a GeoGig repository is analogous to a directory in a filesystem. Features are named as numbers, reflecting the order in which they are found in the source data. It is not necessarily the same order in which they are listed by the ``ls`` command.

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
   #      added  Raleigh_Park_Locations
   #      added  Raleigh_Park_Locations/9
   #      added  Raleigh_Park_Locations/7
   #      added  Raleigh_Park_Locations/8
   ...
   #      added  Raleigh_Park_Locations/75
   #      added  Raleigh_Park_Locations/70
   # 114 total.

Adding data
-----------

To tell GeoGig that you want to version data in the working tree, you have to add it to the **staging area**. Do this by running the following command.

.. code-block:: console

   geogig add

The response will look like this:

.. code-block:: console

   Counting unstaged elements...114
   Staging changes...
   100%
   113 features and 1 trees staged for commit
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
   #      added  Raleigh_Park_Locations
   #      added  Raleigh_Park_Locations/9
   #      added  Raleigh_Park_Locations/7
   #      added  Raleigh_Park_Locations/8
   ...
   #      added  Raleigh_Park_Locations/75
   #      added  Raleigh_Park_Locations/70
   # 114 total.

The staging area is the last area before the data gets versioned in the repository database.

Committing
----------

Committing means to create a new version of the data which is in the staging area.

Type the following command.

.. code-block:: console

   geogig commit -m "first version"

The response will look like this:

.. code-block:: console

   100%
   [11b7058f4b8aaca98036f24c127e929281a01cce] first version
   Committed, counting objects...113 features added, 0 changed, 0 deleted.

The text between quotes after the ``-m`` option is the commit message, which should describe the snapshot in a human-readable format.

Making edits
------------

We will now simulate making an edit to our parks layer. The :file:`parks_plus_1feature/Raleigh_Park_Locations.shp` file contains the same data as the original parks file, but with an added feature. Import this file.

To do this, follow the same procedure as before: import data, add, and then commit.

.. code-block:: console

   geogig shp import parks_plus_1feature/Raleigh_Park_Locations.shp

.. note:: All editing of data must be done externally to GeoGig. If you prefer to make your own edits, you can do so using `QGIS <http://www.qgis.org/en/site/>`_ or any other GIS software you prefer.

If you run the ``status`` command after importing (and before adding), you will see elements which are not yet staged for commits. GeoGig will only report modifications to features that have been changed.

.. code-block:: console

   geogig status

The response will look like this:

.. code-block:: console

   # On branch master
   # Changes not staged for commit:
   #   (use "geogig add <path/to/fid>..." to update what will be committed
   #   (use "geogig checkout -- <path/to/fid>..." to discard changes in working directory
   #
   #      modified  Raleigh_Park_Locations
   #      added  Raleigh_Park_Locations/114
   # 2 total.

Now add the new features:

.. code-block:: console

   geogig add

.. code-block:: console

   Counting unstaged elements...2
   Staging changes...
   100%
   1 features and 1 trees staged for commit
   0 features and 0 trees not staged for commit

Then commit to create a new version:

.. code-block:: console

   geogig commit -m "first modification"

.. code-block:: console

   100%
   [bcafa36c5d6107e6bb95ba8a93fef48800762771] first modification
   Committed, counting objects...1 features added, 0 changed, 0 deleted.

Viewing repository history
--------------------------

You can use the ``log`` command to see the history of your repository. The history consists of the listing of commits, ordered in reverse chronological order (most recent first).

.. code-block:: console

   geogig log

.. code-block:: console

   Commit:  bcafa36c5d6107e6bb95ba8a93fef48800762771
   Author:  Author <author@example.com>
   Date:    (2 minutes ago) 2016-12-17 11:40:04 -0800
   Subject: first modification

   Commit:  11b7058f4b8aaca98036f24c127e929281a01cce
   Author:  Author <author@example.com>
   Date:    (13 minutes ago) 2016-12-17 11:28:57 -0800
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

The ``-c`` option tells GeoGig to not only create the branch, but also, to switch to that branch. Everything done will now be added to this new history line.

.. note:: The default branch is named ``master``.

Now use the :file:`parks_plus_2features/Raleigh_Park_Locations.shp` file. Once again - import, add, and then commit. This shapefile contains the same data as the last version, with yet another feature added on.

.. code-block:: console

   geogig shp import parks_plus_2features/Raleigh_Park_Locations.shp
   geogig add
   geogig commit -m "added new feature"

The ``log`` command will show a history like this:

.. code-block:: console

   Commit:  1466c1c75d51282093b9d85e96b14e9898b74d2f
   Author:  Author <author@example.com>
   Date:    (40 seconds ago) 2016-12-17 11:45:02 -0800
   Subject: added a new feature

   Commit:  bcafa36c5d6107e6bb95ba8a93fef48800762771
   Author:  Author <author@example.com>
   Date:    (5 minutes ago) 2016-12-17 11:40:04 -0800
   Subject: first modification

   Commit:  11b7058f4b8aaca98036f24c127e929281a01cce
   Author:  Author <author@example.com>
   Date:    (16 minutes ago) 2016-12-17 11:28:57 -0800
   Subject: first version

Merging commits from a branch
-----------------------------

Our repository now has two branches: the one we just created (``myedits``) and the default branch (``master``). To see all the branches within a given repository, execute the ``geogig branch`` command.

Let's merge the changes we have just added from the ``myedits`` branch into the ``master`` branch.

First **switch to the branch to which you would like to apply the changes**, in this case it is ``master``. Execute the ``geogig checkout master`` command to switch to the ``master`` branch.

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

   bcafa36c5d6107e6bb95ba8a93fef48800762771 first modification
   11b7058f4b8aaca98036f24c127e929281a01cce first version

Notice that the most recent commit (with the message "added new feature") is missing. This is because it was added to the ``myedits`` branch, not the ``master`` branch (the branch we are currently on).

To merge the work done in the ``myedits`` branch into the current ``master`` branch, enter the following command:

.. code-block:: console

   geogig merge myedits

The response will look like this:

.. code-block:: console

   Checking for possible conflicts...
   1%
   Merging commit 71217cac78d501e0dc120c596bb01a01a0a737d7

   Conflicts: 0, merged: 0, unconflicted: 2
   0%
   [71217cac78d501e0dc120c596bb01a01a0a737d7] added new feature
   Committed, counting objects...1 features added, 0 changed, 0 deleted.

Now we can see that the latest commit introduced into the ``myedits`` branch is also present in ``master``.

.. code-block:: console

   geogig log --oneline

.. code-block:: console

   1466c1c75d51282093b9d85e96b14e9898b74d2f added a new feature
   bcafa36c5d6107e6bb95ba8a93fef48800762771 first modification
   11b7058f4b8aaca98036f24c127e929281a01cce first version

Handling merge conflicts
------------------------

We just saw that the work done on one branch could be merged automatically to another branch without problems. This is not always possible, in which case it needs to be done manually.

To see this in action, create a new branch named ``conflict_res``, and create a commit based on the ``parks_1st_change/Raleigh_Park_Locations.shp`` shapefile.

.. code-block:: console

   geogig branch conflict_res -c
   geogig shp import parks_1st_change/Raleigh_Park_Locations.shp
   geogig add
   geogig commit -m "edits on the conflict_res branch"

This is the same data as ``parks_plus_2features/Raleigh_Park_Locations.shp``, however the new shapefile changes the name for 'Walnut Terrace Park' to 'Walnut Terrace Field'.

Now go back to the ``master`` branch and create a new commit with the data in ``parks_2nd_change/Raleigh_Park_Locations.shp``.

This is the same data as ``parks_plus_2features/Raleigh_Park_Locations.shp``, however the new shapefile changes the name for 'Walnut Terrace Park' to 'Walnut Terrace Grove'.

.. code-block:: console

   geogig checkout master
   geogig shp import parks_2nd_change/Raleigh_Park_Locations.shp
   geogig add
   geogig commit -m "edits on the master branch"

This is a conflict situation, as the same data has been changed in two different manners in the two branches. If you try to merge the ``fix`` branch into ``master``, GeoGig cannot automatically resolve this situation and will fail.

.. code-block:: console

   geogig merge conflict_res

.. code-block:: console

   Checking for possible conflicts...
   1%
   Possible conflicts. Creating intermediate merge status...
   0%

   Saving 1 conflicts...
   CONFLICT: Merge conflict in Raleigh_Park_Locations/1
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
   #      unmerged  Raleigh_Park_Locations/1
   # 1 total.

An **unmerged path** represents a element with a conflict.

You can get more details about the conflict by running the ``conflicts`` command:

.. code-block:: console

   geogig conflicts --diff

The response will look like this:

.. code-block:: console

   ---Raleigh_Park_Locations/1---
   Ours
   NAME: Walnut Terrace Park -> Walnut Terrace Grove

   Theirs
   NAME: Walnut Terrace Park -> Walnut Terrace Field

The output indicates that the value in the ``NAME`` attribute of the ``Raleigh_Park_Locations/1`` feature is causing the conflict.

The conflict has to be solved manually. You will have to merge both versions yourself, or just select one of the versions to be used.

.. todo:: Once we have a UI, show a manual merge using the UI.

Assume we want to use the changed feature in the ``conflict_res`` branch. Since we are in the ``master`` branch, the ``conflict_res`` branch is considered "theirs." Run the following command:

.. code-block:: console

   geogig checkout -p Raleigh_Park_Locations/1 --theirs

The response will look like this:

.. code-block:: console

   Objects in the working tree were updated to the specified version.

That puts the ``conflict_res`` branch version in the working tree, overwriting what was there. This removes the conflict.

.. code-block:: console

   geogig add

.. code-block:: console

   Counting unstaged elements...2
   Staging changes...
   50%
   Building final tree Raleigh_Park_Locations

   Removing 1 merged conflicts...

   Done. 0 unmerged conflicts.
   100%
   1 features and 1 trees staged for commit
   0 features and 0 trees not staged for commit

Now that the conflict has been resolved, you may commit the change. There is no need to add a commit message, since that is created automatically during a merge operation.

.. code-block:: console

   geogig commit


Tagging a version
-----------------

You can add a "tag" to a version to easily identify a snapshot with something more descriptive than the identifier associated with each commit.

To do so, use the ``tag`` command:

.. code-block:: console

   geogig tag my_tag_name -m "First official version"

Now you can refer to a specific version of the data with a name.

.. todo:: Example?

Exporting from a GeoGig repository
----------------------------------

Data can be exported from a GeoGig repository into several formats, ready to be used by external applications.

To export a given tree to a shapefile, use the ``shp export`` command.

.. code-block:: console

   geogig shp export Raleigh_Park_Locations my_parks.shp

.. code-block:: console

   Exporting from Raleigh_Park_Locations to my_parks...
   100%
   Raleigh_Park_Locations exported successfully to my_parks.shp

This will create a file named ``my_parks.shp`` in the current directory that contains the current state of the repository.

Past versions of the data can also be exported by prefixing the tree name with a commit ID and a colon, as in the following example:

.. code-block:: console

   geogig shp export 6bcd72b1a536aa6ec9a773a353f3e4e6f2ffa973:Raleigh_Park_Locations my_older_parks.shp

Use "HEAD" notation to export changes relative to the current working revision. For example, ``HEAD~1`` refers to the second-most recent commit, ``HEAD~2`` refers to the commit prior to that, etc.

.. code-block:: console

   geogig shp export HEAD~1:Raleigh_Park_Locations 2nd_last_version_parks.shp

Synchronizing repositories
--------------------------

A GeoGig repository can interact with other GeoGig repositories that are working with the same data. Other GeoGig repositories are known as **remotes**.

In our situation, we created a new repository from scratch using the ``init`` command. But if we wanted to start with a copy of an existing repository (referred to as the ``origin``), use the ``clone`` command.

Let's clone the repository we have been working on. Create a new directory in your file system, move into it and run the following commands (replace 'YOUR_FIRST_REPO' with the actual name of the first directory created)

.. code-block:: console

   mkdir ../my_new_repo
   cd ../my_new_repo
   geogig clone ../YOUR_FIRST_REPO

The response will look like this:

.. code-block:: console

   Cloning into 'geogig_tutorial'...

   Fetching objects from refs/heads/conflict_res
   1%
   Fetching objects from refs/heads/master

   Fetching objects from refs/heads/myedits

   Fetching objects from refs/tags/my_tag_name
   100%
   Done.

With the repository cloned, you can work here as you would normally and the changes will be placed on top of the changes that already exist from the original repository.

You can merge commits from the ``origin`` repository to this new repository by using the ``pull`` command. This will update the current branch with changes that have been made on that branch in the remote repository since the last time both repositories were synchronized.

.. code-block:: console

   geogig pull origin

To move your local changes from your repository into ``origin``, use the ``push`` command:

.. code-block:: console

   geogig push origin

Tutorial complete
-----------------

Congratulations! You now know the basics of managing data with GeoGig.

Check out the rest of the GeoGig manual in order to learn more!
