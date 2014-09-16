.. _repo.merging:

Merging branches
================

When working on a branch, a typical workflow will eventually involve copying that work onto another branch, most often the ``master`` branch.

Merging is usually done when you have finished working on a given branch and you think the modifications that you have made are ready to become part of the main ``master`` branch. In that case, you will apply all the same changes that you have made to the features and trees in that branch onto the features and trees in the ``master`` branch, so the ``master`` branch will contains changes from both branches.

The following graphic describes this procedure:

.. figure:: ../img/merged.png

Merging is done using the ``geogig merge`` command. Change to the **destination branch** (in most cases, ``master``) and perform the following command:

.. code-block:: console

   geogig merge mybranch

That will add new commits from the ``mybranch`` branch to the ``master`` branch.

Once you have merged your changes, you may wish to delete the branch. Do this by invoking the ``-d`` option on the ``branch`` command.

.. code-block:: console

   geogig branch -d mybranch


Merge conflicts
---------------

When merging two branches, the changes made may not be compatible. For instance, if both branches have modified the same attribute value of the same feature, that will cause a conflicting situation that GeoGig cannot solve automatically. In such a case, GeoGig will merge all the compatible changes and leave the conflicting ones marked, so they can be manually resolved and then committed.

When a merge operation finds conflicts and it cannot automatically merge all the changes, it shows a message like the one shown below:

.. code-block:: console

   CONFLICT: Merge conflict in parks/2
   CONFLICT: Merge conflict in parks/3
   CONFLICT: Merge conflict in parks/1
   Automatic merge failed. Fix conflicts and then commit the result.

You can check which elements are conflicted by running the ``geogig status`` command. The result will be similar to the one shown next:

.. code-block:: console
  
   # On branch master
   # Unmerged paths:
   #   (use "geogig add/rm <path/to/fid>..." as appropriate to mark resolution
   #
   #      unmerged  parks/2
   #      unmerged  parks/3
   #      unmerged  parks/1
   # 3 total.

The above message shows a repository with three conflicted features in its index. If there are unstaged elements, they will also be shown as usual. If the merge operation staged elements that did not cause any conflict, they will appear as ready to be committed. These elements will also be changed in the working tree, to reflect the same version that is stored in the index, which is the result of the automatic merge operation.

When a conflict arises, the merge operation is interrupted. Conflicted elements are marked in the index under the heading **unmerged paths** and the user must solve these manually. This is done by picking a change from one branch or the other, or by some combination of the two.

.. note:: "Unmerged paths" is another name for a conflicted element.

Types of conflicts
------------------

The following cases will create a merge conflict when the following situations occur on both branches:

* Modifying the same attribute of a feature, setting different values.
* Modifying an attribute in a feature (on one branch), deleting that attribute (on another branch).
* Adding different features under the same path.
* Modifying the default feature type for a given path, setting different values.
* Modifying the same geometry of a feature differently.
* One branch has deleted a tree, while the other one has added or modified a feature under that tree.
* Both branches have modified a feature and at least one of them has changed its feature type, and the resulting feature types are not the same.


The following cases will not produce a merge conflict when the following situations occur on both branches:

* Adding the same feature at the same path.
* Deleting the same feature.
* Modifying the same attribute in a feature, setting the same new value.
* Setting the same new default feature type for a path.
* Editing the same feature, but modifying different attributes.
* Modifying the geometry of a feature, but when changes are compatible and can be both incorporated.
* Making the same modification to a feature geometry.

In general, whenever the situation doesn't have a clear way of being solved, GeoGig will report a conflict for the sake of safety.




Showing conflicts
-----------------

The ``conflicts`` command can be used to describe the current unmerged elements. There are three ways of displaying conflicts:

#. Running ``geogig conflicts`` will print the full descriptions of the versions involved in the conflict: the common ancestor, "ours" (current branch) and "theirs" (the branch to merge). The descriptions of the involved elements are the same ones that would be obtained by calling the GeoGig ``cat`` command on each of them.

#. Running ``geogig conflicts --diff`` will output a representation with diff-like syntax instead of full descriptions, showing only those attributes the differ. It uses the same syntax as the ``diff`` command, which is described in the :ref:`differences` section, which makes it easier to see why the conflict arises and how to solve it.

#. Running ``geogig conflicts --ids-only`` will return just the IDs of the three versions corresponding to each unmerged element.

Solving using the merge tool
----------------------------

The most practical way to solve the merge conflicts is using the merge tool.

.. todo:: To be written.

Merging more than two branches
------------------------------

The ``merge`` command accepts more than one single branch name as entry. If several branch names are provided, it will perform what is know as an **octopus merge**.

The following situations will cause GeoGig to fail the octopus merge process:

* A conflict on any type exist, whether it is a conflict between two branches or between many of them at the same time.
* A feature has been edited by at least two branches, and the changes introduced are not identical. Even if the changes are compatible (for instance, if branches have edited values for different attributes), GeoGig will not perform an automerge in this case, and the merge operation won't be executed.

In these cases, you will have to merge branches individually, solving conflicts for each of them one be on.

Unlike a standard two branch merge, where the process will partially complete, a conflict will cause an octopus merge to *not even start*.


Staging a merged version of an conflicted element 
-------------------------------------------------

Using the ``geogig add`` command, features can be staged in the usual way. When a feature is staged, it is not in a conflicted state. Running the ``add`` command will solve the conflicted merge. Before this can be done, you must pick which version of the commit to merge.

GeoGig uses "ours" and "theirs" notation, with "ours" referring to the current branch, and "theirs" referring to the other branch.

To select the feature from the current branch: 

.. code-block:: console

  geogig checkout -p <path_to_feature> --ours

To select the feature from the other branch:

.. code-block:: console

  geogig checkout -p <path_to_feature> --theirs

Alternately, you can import a new feature using one of the importing tools from GeoGig, or delete the conflicted feature entirely using the ``rm`` command. The latter option will remove it from both the working tree and the index, as well as the commit.. Their is no need to call ``add`` afterwards, unless you have staged some other element to solve a different conflict.

Once you have the correct version that you want to commit, run ``add`` to stage it and then run ``commit`` to finally commit your resolved elements and finish the merge.

In a standard commit, you usually must supply a commit message using the ``-m`` switch. In the case of a merge, you can also run ``geogig merge`` without a commit message. In the conflict case, the default message would look something like this:

.. code-block:: console

   Merge branch refs/heads/b1

   Conflicts:
     parks/2
     parks/3
     parks/1


Aborting the merge operation
----------------------------

You can abort the merge operation and restore it to the original state it had before you invoked the ``merge`` command. You have the following alternatives, which will cause the same result.

.. code-block:: console

   geogig reset --hard ORIG_HEAD
   geogig merge --abort

.. warning:: This is not like git. The ``--abort`` here is just a reset operation.



.. Commenting this out here for now but saving it

::

  geogig conflicts
  $geogig conflicts
  parks/2

  Ancestor    27207309879802a99d161b063b8f958d179be3b0
  FEATURE
  id    27207309879802a99d161b063b8f958d179be3b0
  STRING    Medford School District
  DOUBLE    53935.8939996
  DOUBLE    1004.9211325
  STRING    Kennedy Elementary
  LONG    0
  STRING    Medford School District
  STRING    School Field
  MULTIPOLYGON    MULTIPOLYGON (((-122.84163143974176 42.35985624789982, -122.84146965654989 42.35985609227347, -122.84117673733482 42.35985565827537, -122.8409230724077 42.35985528171881, -122.84062434545373 42.35985483812396, -122.84034728245699 42.35985442523742, -122.8403468719201 42.35943411552068, -122.84163015984652 42.35942328456196, -122.8416300075414 42.359625066567794, -122.84163143974176 42.35985624789982)))
  STRING    Public


  Ours    d8cc931603603bd64506880dc1760b372808ef2d
  FEATURE
  id    d8cc931603603bd64506880dc1760b372808ef2d
  STRING    Medford School District
  DOUBLE    53935.8939996
  DOUBLE    1004.9211325
  STRING    Kennedy Elementary
  LONG    5
  STRING    Medford School District
  STRING    School Field
  MULTIPOLYGON    MULTIPOLYGON (((-122.84163143974176 42.35985624789982, -122.84146965654989 42.35985609227347, -122.84117673733482 42.35985565827537, -122.8409230724077 42.35985528171881, -122.84062434545373 42.35985483812396, -122.84034728245699 42.35985442523742, -122.8403468719201 42.35943411552068, -122.84163015984652 42.35942328456196, -122.8416300075414 42.359625066567794, -122.84163143974176 42.35985624789982)))
  STRING    Public


  Theirs    a77e46d2ad6e2c9eef3b6e5191a6c299037d602c
  FEATURE
  id    a77e46d2ad6e2c9eef3b6e5191a6c299037d602c
  STRING    Medford School District
  DOUBLE    53935.8939996
  DOUBLE    1004.9211325
  STRING    Kennedy Elementary
  LONG    2
  STRING    Medford School District
  STRING    School Field
  MULTIPOLYGON    MULTIPOLYGON (((-122.8434107328942 42.36043884831257, -122.84324894970233 42.360438692686216, -122.84295603048726 42.36043825868812, -122.84270236556014 42.360437882131556, -122.84240363860617 42.36043743853671, -122.84212657560943 42.36043702565017, -122.84212616507254 42.360016715933426, -122.84340945299896 42.36000588497471, -122.84340930069384 42.36020766698054, -122.8434107328942 42.36043884831257)))
  STRING    Public
