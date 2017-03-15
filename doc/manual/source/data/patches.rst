.. _patches:

Creating and applying patches
==============================

Patches are files that store the differences that exists between two states, A and B, of a repository. Patches can be applied to another repository to alter it based on those differences. Since patches are self-contained, a patch can be created to store a set of changes, sent, and applied to a different repository. So this second repository will then see those changes reflected in its working tree.

GeoGig patches are created using the ``format-patch`` command. The syntax of this command is similar to the ``diff`` command. Instead of being printed, differences are written to a patch file, which has to be defined using the ``-f`` option. The following example shows the most basic usage.

::

	$ geogig format-patch -f changes.patch

This will create a patch file named ``changes.patch``, which contains the differences between the index and the current working tree.

To store the differences that correspond to a single path named ``roads``, the following command should be used.

::

	$ geogig format-patch -f changes.path -- roads

Once the patch is created, it can be applied using the ``apply`` command. To apply the patch created in the previous example, the following command should be executed.

::

	$ geogig apply changes.patch

If the patch can be applied, the working tree will be changed according to the differences stored in the patch. If not, GeoGig will inform the user that the patch cannot be applied.

A patch can be applied onto the working tree of a repository if it meets the following conditions.

- Features to be added do not previously exist on the working tree
- Features to be removed already exist on the working tree
- Features to be modified already exist on the working tree
- If the modification involves changing the value of a given attribute, the previous value matches the value stored in the patch as old value.
- If the modification involves adding a new attribute and to the feature type its corresponding value in the feature, the feature does not have that attribute
- If the modification involves removing an attribute, the feature must already contain that attribute.

Before applying the patch, we can check that it can be applied on the current index by running the ``apply`` command with the ``--check`` modifier.

::

	$ geogig apply --check changes.path


If the patch cannot be applied, GeoGig will show a list of conflicting changes along with the changes which can be safely applied.

::

	$ geogig apply --check changes.patch
	Error: Patch cannot be applied

	Applicable entries:

	M    parks/34    a745eadaeda8ca18b1ff544e1cf429e48d2b2697
	perimeter    A    0.016849
	area    M    732342.64254    7.430135156027973E-6

	Conflicting entries:

	M    parks/13    a745eadaeda8ca18b1ff544e1cf429e48d2b2697
	perimeter    A    0.009355
	area    M    364587.870651    3.700497018144233E-6


A summary of all changes included in a patch can be obtained by running the command with the ``--summary`` option.

If you want to apply the patch, ignoring conflicting changes, and only use the changes that are safe to apply, you can use the ``--reject`` option. This will not only apply the patch partially, but also generate a new patch file with the same name of the one to apply, adding the ``.rej`` suffix. This new patch contains just the rejected changes that could not be applied on the current repository.

Patches can be applied in reverse using the ``--reverse`` option. A reversed patch has exactly the opposite effect as the direct patch. Applying a reversed patch after applying the same patch in normal mode will leave the working tree in its original state. For this reason, reversed patches can be used to undo changes. A common case of that is applying a newer patch that contains the changes of a previous one already applied. The first patch can be applied in reverse to undo changes, and then the most recent patch applied on the original state of the repository.

The ``--reverse`` option is compatible with other options of the ``apply`` command, so a summary of the reversed patch can be obtained by calling using both options together.

::

	$ geogig --reverse --summary changes.patch


Difference between patch files and output of ``diff`` command
--------------------------------------------------------------

Unlike in the case of using git, redirecting the output of the GeoGig ``diff`` command to a file will not create a valid patch that can later be used with the ``apply`` command. This is due to the different nature of the data that is handled by GeoGig. The ``diff`` command tries to create a human-readable version of the difference, but does not include some information needed to apply the patch, such as full descriptions of features types that are created by the patch, or a full listing of modified coordinates, in case a geometry has been modified.

When describing the content of a patch using the ``--summary`` option, the summary is presented in the human-readable version, although in the case of modified geometries the result is not exactly the same one output by the ``diff`` command.

The following text corresponds to the summary of a single modification in a patch.

::

	M    parks/22
	parktype    "Garden" -> "Park"
	the_geom    0 point(s) deleted, 1 new point(s) added, 3 point(s) moved

The corresponding patch content, however, is different, as it can be seen below.

::

	M	parks/22	6350a6955b124119850f5a6906f70dc02ebb31c9	6350a6955b124119850f5a6906f70dc02ebb31c9
	parktype	M	"Garden"	"Park"
	the_geom	M	0/1/3	@@ -76,20 +76,20 @@\n 2.30\n+40695\n 3\n-3782\n 9\n+54\n 3\n-01001\n+24\n , -1\n@@ -1783,27 +1783,28 @@\n 2.84\n-25\n+3\n 4\n-62\n+888\n 6\n+9\n 00\n-999\n+885\n  42.2984\n 2718\n@@ -1803,18 +1803,18 @@\n 2984\n-2\n+6488\n 7\n-1\n+3976\n 8\n-304132\n , -1\n@@ -1858,16 +1858,55 @@\n  -122.84\n+467141796014 42.29932750407095, -122.84\n 55347783\n@@ -1941,35 +1941,35 @@\n .845\n-526479\n 16\n+200\n 3\n-3\n+71851\n  42.29\n-81311\n+7\n 4\n-1\n+27\n 3\n+2\n 6\n-92\n+717\n 2, -\n

The ouput of the ``diff`` command for that same modification will be as show next.

::

	6350a6... 6350a6... 37d757... e0c9ec...   M  parks/22
	parktype: "Garden" -> "Park"
	the_geom: -122.8456209953915,42.30337924298179 [-122.84288079012158,42.30337829301001] (-122.84288079012158,42.30406953954324) -122.84062871947361,42.303377484342896 -122.84402124697434,42.29758391876194 [-122.8425462600999,42.29842718304132] (-122.84348886900885,42.29846488739768) -122.84403459217438,42.299849758882665 (-122.84467141796014,42.29932750407095) -122.84553477839586,42.29898797978287 [-122.8455264791633,42.29813114136922] (-122.84516200371851,42.2974273267172) -122.84562615512898,42.29822488745995 -122.84562739414677,42.29869863907225 -122.84562865934777,42.29887914225196 -122.84562746190791,42.29938631374956 -122.84562038858874,42.29949283132804 -122.84562093251843,42.30009514260314 -122.84561954067343,42.300597171392674 -122.84562020542222,42.3019375538702 -122.84562986430872,42.302242481706564 -122.84562420177575,42.3031702527034 -122.84562286340557,42.30323277320125 -122.8456209953915,42.30337924298179 @-122.84070544530037,42.29863971301672 -122.84066916748334,42.298658265906056 -122.84114150211431,42.29917259286536 -122.84175134391927,42.2988708450611 -122.84154879818728,42.298647383830684 -122.84162235642542,42.29829337633362 -122.8412561201354,42.297896172348146 -122.8417060301776,42.29766808625502 -122.8412177383454,42.29741710209413 -122.84103109529498,42.29732116850443 -122.84088785596595,42.29783772129766 -122.8398848619697,42.29795997303185 -122.83999152040946,42.29828853706577 -122.84070544530037,42.29863971301672

Since the syntax used for describing differences stored in a patch is similar to that of the ``diff`` command (except, as mentioned above, in the case of geometries, where ``diff`` shows a full list of all coordinates, while the patch summary just shows the number of affected points) checking the documentation for the ``diff`` command is recommended.
