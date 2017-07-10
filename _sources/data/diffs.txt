.. _differences:

Showing differences
===================

The differences between two entries in a GeoGig repository can be displayed using the ``diff`` command. In the most common case, this is used to show the differences between the working tree and the HEAD of the repository, so as to know which changes will be introduced if the current modifications in the current working tree are staged and committed. The HEAD will represent in this case the *old* version, while the working tree will represent the *new* version.

For this task, the most basic usage of the ``diff`` command requires no options at all

::

	$ geogig diff


This will produce something like the output shown in the example below.

::

	6350a6... 6350a6... 37d757... e0c9ec...   M  parks/22
	parktype: "Garden" -> "Park"
	the_geom: -122.8456209953915,42.30337924298179 [-122.84288079012158,42.30337829301001] (-122.84288079012158,42.30406953954324) -122.84062871947361,42.303377484342896 -122.84402124697434,42.29758391876194 [-122.8425462600999,42.29842718304132] (-122.84348886900885,42.29846488739768) -122.84403459217438,42.299849758882665 (-122.84467141796014,42.29932750407095) -122.84553477839586,42.29898797978287 [-122.8455264791633,42.29813114136922] (-122.84516200371851,42.2974273267172) -122.84562615512898,42.29822488745995 -122.84562739414677,42.29869863907225 -122.84562865934777,42.29887914225196 -122.84562746190791,42.29938631374956 -122.84562038858874,42.29949283132804 -122.84562093251843,42.30009514260314 -122.84561954067343,42.300597171392674 -122.84562020542222,42.3019375538702 -122.84562986430872,42.302242481706564 -122.84562420177575,42.3031702527034 -122.84562286340557,42.30323277320125 -122.8456209953915,42.30337924298179 @-122.84070544530037,42.29863971301672 -122.84066916748334,42.298658265906056 -122.84114150211431,42.29917259286536 -122.84175134391927,42.2988708450611 -122.84154879818728,42.298647383830684 -122.84162235642542,42.29829337633362 -122.8412561201354,42.297896172348146 -122.8417060301776,42.29766808625502 -122.8412177383454,42.29741710209413 -122.84103109529498,42.29732116850443 -122.84088785596595,42.29783772129766 -122.8398848619697,42.29795997303185 -122.83999152040946,42.29828853706577 -122.84070544530037,42.2986397130167

	6350a6... 6350a6... 887b4a... fad2fa...   M  parks/12
	the_geom: [-122.83646412838807,42.36016644633764 -122.83706843181271,42.36018038487805 -122.83740062537728,42.360187694790284 -122.83773129525122,42.36019528458837 -122.83795404148778,42.36020136945975 -122.83819236923999,42.36020660256662 -122.83846546872873,42.360518040102995 -122.83876233613934,42.36084768643743 -122.83979986790222,42.361999744796655 -122.83876583032126,42.36206395843249 -122.8387666181915,42.36241475445113 -122.8350544594257,42.362400655348836 -122.83505311158638,42.36190072779918 -122.8352814492704,42.36189781560542 -122.83546514962634,42.36183970799634 -122.8355995051357,42.361675638841625 -122.83649163970789,42.36166473464665 -122.83646412838807,42.36016644633764] (-122.83765019705244,42.36225229123015 -122.83825450047708,42.36226622977056 -122.83858669404165,42.362273539682796 -122.83891736391558,42.362281129480884 -122.83914011015214,42.362287214352264 -122.83937843790436,42.36229244745913 -122.8396515373931,42.36260388499551 -122.8399484048037,42.36293353132994 -122.84098593656658,42.36408558968917 -122.83995189898563,42.364149803325006 -122.83995268685587,42.36450059934364 -122.83624052809007,42.36448650024135 -122.83623918025074,42.363986572691694 -122.83646751793476,42.36398366049793 -122.83665121829071,42.36392555288885 -122.83678557380007,42.36376148373414 -122.83767770837225,42.363750579539165 -122.83765019705244,42.36225229123015) /


Modified features
------------------


The above text indicates that 2 features under the ``parks`` tree (``12`` and ``22``) have been modified. The first line corresponding to each modification, they both have a similar syntax. Below is the explanation of each value in the case of the first difference.

- ``6350a6...``: ID of the feature type of the 'old' version
- ``6350a6...``: ID of the feature type of the 'new' version. Since the feature type hasn't changed, this is the same as the previous value.
- ``887b4a...``: ID of the of the 'old' version of the feature
- ``fad2fa...``: ID of the of the 'new' version of the feature
- ``M``. Type of difference. ``M`` indicates that the feature has been modified, that is, some of its attributes have changed or its feature type has changed. It can also be ``A``, which denotes an addition (a new feature that did not exist before on that path), or ``R``, which denotes a removal (a feature that existed on that path in the old version, but is not present in the new one)
- ``parks/parks.12``: full path of the feature.

All IDs are shortened to the first 6 characters.

After the header line, the following lines describe the changes in those attributes that have been modified. For each line, the name of the modified parameter is followed by the description of the modification. In the case of attributes that do not contain geometries, the modification is expressed showing the old and new values. From the two features modified in the above example, only ``parks.22`` contains a non-geometry field (named ``parktype``) which has been modified.

::

	parktype: "Garden" -> "Park"

If the modification involves removing an attribute from the feature type, the word ``[MISSING]`` is shown as the new value.

::

	parktype: "Garden" -> [MISSING]

If the terminal supports ANSI escape codes, that line will be shown in red.

If the modification involves adding an attribute from the feature type, the word ``[MISSING]`` is shown as the old value.

::

	parktype: [MISSING] -> "Park"

If the terminal supports ANSI escape codes, that line will be shown in green.

In the case of fields containing geometries, a structured list of all coordinates defining the geometry are shown. Coordinates added are shown between brackets, while removed coordinates are shown between square brackets. In ANSI-supporting terminals, removals are shown in red and additions are shown in green.

The structure of the text representing the geometry is as follows:

- It starts with the type name of the geometry, followed by the list of coordinates
- Coordinates are x,y pairs, separated by a whitespace
- In the case of multi-geometries, sub-geometries are separated by the slash (`/`) sign. For instance, `MultiLineString 0,10 0,20 0,30 / 10,10 50,65`` represents a multi-line with two lines
- In the case of polygons, the first string of coordinates represents the outer ring, and inner rings are added next, delimited by the ``@`` sign. For instance, ``MultiPolygon 40.0,40.0 20.0,45.0 45.0,30.0 40.0,40.0 / 20.0,35.0 45.0,20.0 30.0,5.0 10.0,10.0 10.0,30.0 20.0,35.0 @ 30.0,20.0 20.0,25.0 20.0,15.0 30.0,20.0`` represents a geometry with two polygons, the last one of them with an inner ring.

To avoid large geometries cluttering the result, the ``--nogeom`` option can be used. Instead of showing the full list of coordinates, it will show a summary of changes made for the corresponding attributes.

The above example will look as shown next.

::

	$ geogig diff --nogeom
	6350a6... 6350a6... 37d757... e0c9ec...   M  parks/22
	the_geom: 0 point(s) deleted, 1 new point(s) added, 3 point(s) moved
	parktype: "Garden" -> "Park"

	6350a6... 6350a6... 887b4a... fad2fa...   M  parks/12
	the_geom: 0 point(s) deleted, 0 new point(s) added, 18 point(s) moved

Added and removed features
---------------------------

In case the difference includes new features added or old ones removed, the syntax will look like the example shown next.

::

	$ geogig diff
	000000... 6350a6... 000000... 95677d...   A  parks/49
	agency    "City Of Medford"
	area    362583.12247
	len    2589.77110627
	name    "Bear Creek Greenway"
	number_fac    0
	owner    "City Of Medford"
	parktype    "Park"
	the_geom    MULTIPOLYGON (((-122.87967788576663 42.35313303427821, -122.87875631536167 42.353132085257435, -122.8781124540551 42.3507669501685, -122.87840825291133 42.35076003147308, -122.87997783607581 42.35072334448266, -122.87996842132236 42.35313334287105, -122.87967788576663 42.35313303427821)))
	usage    "Public"

	6350a6... 000000... 6997bd... 000000...   R  parks/23

Removals are just indicated with the header line and no additional information. Notice the null ID of the new object.

Additions are described with the full printing of the object added, represented as a list of ``(attribute_name, attribute_value)`` pairs, similar to the one produced by the ``show`` command. A raw description of the feature and its feature type can be obtained using the ``cat`` command and the feature and feature type Id's provided by the ``diff`` command. Notice that, in this case, the old object has null Id's for both the feature and feature type. The one corresponding to the feature type, however, doesn't have to be necessarily null, as it might already exist another feature with that feature type in the repository, prior to adding the one described by this ``diff`` output.

A summary mode is available, by using the ``--summary`` option. When used, only the header line of each modification will be shown.


Showing differences between specific commits
---------------------------------------------

The default behavior of the diff command is to take the working tree as the new version and the HEAD of the repository as the old version. This can be changed by specifying different references, as in the next example.

::

	$ geogig diff b2a780d7c0 HEAD
	6350a6... 000000... 6997bd... 000000...   R  parks/23

This will compare a previous commit (with the ID specified as the first reference) with the current head of the repository. IDs used with this syntax must resolve to a commit.

The first entered ID is used as the ID of the old version. Reversing the order of the references will describe the inverse difference.

::

	$ geogig diff HEAD b2a780d7c0
	000000... 6350a6... 000000... 6997bd...   A  parks/23
	agency    "City Of Medford"
	area    44498.3268449
	len    835.779693849
	name    "Heitkamp Property"
	number_fac    0
	owner    "City Of Medford"
	parktype    "Park"
	the_geom    MULTIPOLYGON (((-122.84070544530037 42.29863971301672, -122.84121706601762 42.298380924920195, -122.84145235102078 42.298541042984006, -122.84154879818728 42.298647383830684, -122.84175134391927 42.2988708450611, -122.84114150211431 42.29917259286536, -122.84066916748334 42.298658265906056, -122.84070544530037 42.29863971301672)))
	usage    "Public"

If one of the references is omitted, the supplied reference will be taken as the old version and compared with the working tree.

Comparing against the index instead of the working tree can be done by using the ``--cached`` option. In this case, only one commit reference can be used.

Showing differences for a given path or feature
------------------------------------------------

By default, all the differences between the specified commits (or the working tree and the index if not specified) are shown. Partial differences corresponding to a given path can be obtained by using the ``--`` option followed by the path to compare, as in the following example.

::

	$ geogig diff --path parks

This will just list the differences in the ``parks`` path.

The path can point to a single feature, as in the command line below

::

	$ geogig diff --path parks/1


Notice that, in this case, GeoGig will not complain if the path does not resolve to anything. It will tell you that there are no differences between the selected versions to compare, since the specified feature is missing in both of them.
