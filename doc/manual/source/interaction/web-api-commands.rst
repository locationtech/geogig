Web API: Commands
#################

.. _command_add:

Add (-T)
========

Moves features from the working tree to the staging area to prepare them for committing.

::

    GET /repos/<repo>/add?transactionId=<transactionId>[&path=<pathToFeature>]


Parameters
----------

**path:**
Optional. If specified, only the features or tree named will be added to the staging area.  If not specified, all unstaged changes will be staged.

Examples
--------
**Stage all unstaged changes**

::

    $ curl -v "http://localhost:8182/repos/repo1/add?transactionId=145708c3-aad2-4695-afa0-f7dd91ea91a5" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Add>Success</Add>
    </response>
    
**Stage a single feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/add?transactionId=145708c3-aad2-4695-afa0-f7dd91ea91a5&path=points/d06e56902" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Add>Success</Add>
    </response>
    
**Stage all features of a given type**

::

    $ curl -v "http://localhost:8182/repos/repo1/add?transactionId=145708c3-aad2-4695-afa0-f7dd91ea91a5&path=points" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Add>Success</Add>
    </response>
    

.. _command_blame:

Blame
=====

Returns all of the attribute values of a feature and the commit within which they were last changed.

::

    GET /repos/<repo>/blame?path=<pathToFeature>[&commit=<branchOrCommit>]
    
Parameters
----------

**path:**
Mandatory.  The path of the feature to perform the operation on.

**commit:**
Optional.  The branch or commit to blame from.  If not specified, the ``HEAD`` commit will be used.

Examples
--------

**Perform blame on a feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/blame?path=points/d06e56902" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Blame>
        <Attribute>
          <name>str_attr</name>
          <value>Point 2</value>
          <commit>
            <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
            ...
          </commit>
        </Attribute>
        <Attribute>
          <name>num_attr</name>
          <value>34</value>
          <commit>
            <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
            ...
          </commit>
        </Attribute>
        <Attribute>
          <name>the_geom</name>
          <value>POINT (-60.948837209302326 -11.134883720930318)</value>
          <commit>
            <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
            ...
          </commit>
        </Attribute>
      </Blame>
    </response>
    
**Perform blame on a feature from an alternate commit**

::

    $ curl -v "http://localhost:8182/repos/repo1/blame?path=points/d06e56902&commit=HEAD~1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Blame>
        <Attribute>
          <name>str_attr</name>
          <value>Point 2</value>
          <commit>
            <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
            ...
          </commit>
        </Attribute>
        <Attribute>
          <name>num_attr</name>
          <value>32</value>
          <commit>
            <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
            ...
          </commit>
        </Attribute>
        <Attribute>
          <name>the_geom</name>
          <value>POINT (-60.948837209302326 -11.134883720930318)</value>
          <commit>
            <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
            ...
          </commit>
        </Attribute>
      </Blame>
    </response>


.. _command_branch:

Branch
======

List the branches of a repository, or create a new one.

::

    GET /repos/<repo>/branch?list=true[&remotes=<true|false>]
    GET /repos/<repo>/branch?branchName=<branchName>[&source=<branchOrCommit>]
    
Parameters
----------

**list:**
Mandatory when listing branches.  If specified as ``true``, the branches of the repository will be listed.  Otherwise, ``branchName`` must be specified in order to create a new branch.

**remotes:**
Optional.  Only valid if ``list`` is set to ``true``.  If ``true``, remote branches will be listed along with the remote they belong to.  Local branches will also be listed.

**branchName:**
Mandatory when creating a branch. Only valid if ``list`` is not set to ``true``.  Specifies the name of the new branch to create.

**source:**
Optional.  Only valid if ``branchName`` is specified.  Specifies the branch or commit to base the new branch on.  If not specified, the ``HEAD`` commit will be used.

Examples
--------

**List local branches**

::

    $ curl -v "http://localhost:8182/repos/repo1/branch?list=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Local>
        <Branch>
          <name>branch1</name>
        </Branch>
        <Branch>
          <name>branch2</name>
        </Branch>
        <Branch>
          <name>master</name>
        </Branch>
      </Local>
      <Remote/>
    </response>

**List local and remote branches**

::

    $ curl -v "http://localhost:8182/repos/repo1/branch?list=true&remotes=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Local>
        <Branch>
          <name>branch1</name>
        </Branch>
        <Branch>
          <name>master</name>
        </Branch>
      </Local>
      <Remote>
        <Branch>
          <remoteName>origin</remoteName>
          <name>branch1</name>
        </Branch>
        <Branch>
          <remoteName>origin</remoteName>
          <name>branch2</name>
        </Branch>
        <Branch>
          <remoteName>origin</remoteName>
          <name>master</name>
        </Branch>
      </Remote>
    </response>

**Create a new branch**

::

    $ curl -v "http://localhost:8182/repos/repo1/branch?branchName=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <BranchCreated>
        <name>branch1</name>
        <source>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</source>
      </BranchCreated>
    </response>

**Create a new branch based on an alternate commit**

::

    $ curl -v "http://localhost:8182/repos/repo1/branch?branchName=branch2&source=HEAD~2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <BranchCreated>
        <name>branch2</name>
        <source>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</source>
      </BranchCreated>
    </response>


.. _command_cat:
	   
Cat
===

Returns information about the GeoGig object with the provided ID.  This can be a commit, tree, feature, feature type, or tag.

::

    GET /repos/<repo>/cat?objectid=<objectId>

Parameters
----------

**objectid:**
Mandatory.  The ID of the GeoGig object to describe.

Examples
--------

**Describe a commit**

::

    $ curl -v "http://localhost:8182/repos/repo1/cat?objectid=4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commit>
        <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
        <tree>630831297cb8831ca744db7543510affdde20229</tree>
        <parents>
          <id>71140aa1439d74312165ca16fc852d5138bea5e7</id>
        </parents>
        <author>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506564413249</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </author>
        <committer>
          <name>GeoGig Server</name>
          <email>server@geogig.org</email>
          <timestamp>1506564413249</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </committer>
        <message><![CDATA[added points/b38e3abb1 and points/d06e56902]]></message>
      </commit>
    </response>

**Describe a tree**

::

    $ curl -v "http://localhost:8182/repos/repo1/cat?objectid=630831297cb8831ca744db7543510affdde20229" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <tree>
        <id>630831297cb8831ca744db7543510affdde20229</id>
        <size>2</size>
        <numtrees>1</numtrees>
        <subtree>
          <name>points</name>
          <type>TREE</type>
          <objectid>32f9b9ef6783cffc12dc8dd062403cfb2a9229fb</objectid>
          <metadataid>e4bce1331e8b2b4a59f81f421c77781a2585b686</metadataid>
        </subtree>
      </tree>
    </response>

**Describe a feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/cat?objectid=568e38e7b18e64a027342fe1046b1bb371eac7c7" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <feature>
        <id>568e38e7b18e64a027342fe1046b1bb371eac7c7</id>
        <attribute>
          <type>POINT</type>
          <value>POINT (-101.67906976744186 -19.339534883721)</value>
        </attribute>
        <attribute>
          <type>STRING</type>
          <value>Point 1</value>
        </attribute>
        <attribute>
          <type>INTEGER</type>
          <value>15</value>
        </attribute>
      </feature>
    </response>

**Describe a feature type**

::

    $ curl -v "http://localhost:8182/repos/repo1/cat?objectid=e4bce1331e8b2b4a59f81f421c77781a2585b686" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <featuretype>
        <id>e4bce1331e8b2b4a59f81f421c77781a2585b686</id>
        <name>http://www.opengis.net/gml:points</name>
        <attribute>
          <name>the_geom</name>
          <type>POINT</type>
          <minoccurs>0</minoccurs>
          <maxoccurs>1</maxoccurs>
          <nillable>true</nillable>
          <crs>EPSG:4326</crs>
        </attribute>
        <attribute>
          <name>str_attr</name>
          <type>STRING</type>
          <minoccurs>0</minoccurs>
          <maxoccurs>1</maxoccurs>
          <nillable>true</nillable>
        </attribute>
        <attribute>
          <name>num_attr</name>
          <type>INTEGER</type>
          <minoccurs>0</minoccurs>
          <maxoccurs>1</maxoccurs>
          <nillable>true</nillable>
        </attribute>
      </featuretype>
    </response>

**Describe a tag**

::

    $ curl -v "http://localhost:8182/repos/repo1/cat?objectid=b6dbb92f7f96e1dea36c2c834e53cd602e5ef6a8" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <tag>
        <id>b6dbb92f7f96e1dea36c2c834e53cd602e5ef6a8</id>
        <commitid>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</commitid>
        <name>tag1</name>
        <message>MyTagMessage</message>
        <tagger>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506567453031</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </tagger>
      </tag>
    </response>


.. _command_checkout:

Checkout (-T)
=============

Checkout the specified branch or a single feature during conflict resolution.

::

    GET /repos/<repo>/checkout?transactionId=<transactionId>&branch=<branchName>
    GET /repos/<repo>/checkout?transactionId=<transactionId>&path=<pathToFeature>&<ours=true|theirs=true>
    
Parameters
----------

**branch:**
Optional.  The branch to checkout.  If not specified, ``path`` must be specified to checkout a feature during conflict resolution.

**path:**
Optional.  Only valid during conflict resolution.  The path of a feature to checkout during conflict resolution.

**ours:**
Optional.  Only valid when ``path`` is specified.  Checkout the version of the feature on ``our`` side of the merge.

**theirs:**
Optional.  Only valid when ``path`` is specified.  Checkout the version of the feature on ``their`` side of the merge.

.. note::  If ``path`` is specified then you MUST specify either ``ours`` or ``theirs``.

Examples
--------

**Checkout a branch**

::

    $ curl -v "http://localhost:8182/repos/repo1/checkout?transactionId=11b1088c-6dfe-4377-872a-b64c538fbca0&branch=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <OldTarget>refs/heads/master</OldTarget>
      <NewTarget>branch1</NewTarget>
    </response>

**Resolve a conflict**

::

    $ curl -v "http://localhost:8182/repos/repo1/checkout?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&path=points/d06e56902&ours=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Path>points/d06e56902</Path>
      <Strategy>ours</Strategy>
    </response>


.. _command_commit:

Commit (-T)
===========

Commit staged changes to the transaction and returns the commit ID and a count of the things that were added, changed, and deleted.

::

    GET /repos/<repo>/commit?transactionId=<transactionId>[&message=<commitMessage>][&all=<true|false>][&authorName=<authorName>][&authorEmail=<authorEmail>]
    
Parameters
----------

**message:**
Optional.  The message to use for the commit.  If not specified, no commit message will be used.

**all:**
Optional.  If set to ``true``, all staged and unstaged changes will also be committed.

**authorName:**
Optional.  If specified, the provided author name will be used for the commit.  Otherwise the committer name will be used.

**authorEmail:**
Optional.  If specified, the provided author email will be used for the commit.  Otherwise the committer email will be used.

Examples
--------

**Commit staged changes**

::

    $ curl -v "http://localhost:8182/repos/repo1/commit?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&message=MyMessage" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commitId>fe2b96c6f4b48dfe856c30cc97632025a38bd61c</commitId>
      <added>0</added>
      <changed>1</changed>
      <deleted>0</deleted>
    </response>


.. _command_config:

Config
======

View and set config options on the GeoGig repository.

::

    GET /repos/<repo>/config[?name=<configKey>]
    POST /repos/<repo>/config?name=<configKey>&value=<configValue>

Parameters
----------

**name:**

*GET*

Optional.  If specified, the entry that matches the config key will be returned, otherwise all local config entries will be returned.

*POST*

Mandatory.  The key of the config entry to set.

**value:**

*POST*

Mandatory.  The value of the config entry to set.

Examples
--------

**List all local config entries**

::

    $ curl -v "http://localhost:8182/repos/repo1/config" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <config>
        <name>storage.index</name>
        <value>rocksdb</value>
      </config>
      <config>
        <name>rocksdb.version</name>
        <value>1</value>
      </config>
      <config>
        <name>repo.name</name>
        <value>repo1</value>
      </config>
      <config>
        <name>storage.objects</name>
        <value>rocksdb</value>
      </config>
      <config>
        <name>storage.refs</name>
        <value>file</value>
      </config>
      <config>
        <name>file.version</name>
        <value>1.0</value>
      </config>
    </response>

**Get a config entry**

::

    $ curl -v "http://localhost:8182/repos/repo1/config?name=repo.name" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <value>repo1</value>
    </response>

**Set a config entry**

::

    $ curl -X POST -v "http://localhost:8182/repos/repo1/config?name=my.key&value=my.value" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
    </response>


.. _command_diff:

Diff
====

Returns a comparison of two different versions of a repository.

::

    GET /repos/<repo>/diff?oldRefSpec=<branchOrCommit>[&newRefSpec=<branchOrCommit>][&pathFilter=<path>][&showGeometryChanges=<true|false>][&page=<pageNumber>][&show=<elementsPerPage>]

Parameters
----------

**oldRefSpec:**
Mandatory.  The old branch or commit to perform a diff against.

**newRefSpec:**
Optional.  The new branch or commit to perform a diff against.  If not specified, the ``HEAD`` commit will be used.

**pathFilter:**
Optional.  If specified, only changes made on the given path will be returned.

**showGeometryChanges:**
Optional.  If set to ``true``, the actual geometry of the features will be returned as part of the diff.

**page:**
Optional.  Page number of the results to view.  If the number of changes in the diff exceed the value of ``show``, the results will be paged and must be retrieved with multiple requests.  If not specified, the first page of results will be returned.  If there are additional pages, the response will contain a ``nextPage`` element with a value of ``true``.

**show:**
Optional.  Number of changes to show per page.  If not specified, 30 changes will be shown per page.

Examples
--------

**Perform a diff**

::

    $ curl -v "http://localhost:8182/repos/repo1/diff?oldRefSpec=HEAD~2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <diff>
        <changeType>MODIFIED</changeType>
        <newPath>points/b38e3abb1</newPath>
        <newObjectId>568e38e7b18e64a027342fe1046b1bb371eac7c7</newObjectId>
        <path>points/b38e3abb1</path>
        <oldObjectId>ed0a1c03414c3ae1e56c98fa0ca4613fd446128f</oldObjectId>
      </diff>
      <diff>
        <changeType>MODIFIED</changeType>
        <newPath>points/d06e56902</newPath>
        <newObjectId>ba7db31656fa1abb7e09a0e37ca4c4a9681dbcdb</newObjectId>
        <path>points/d06e56902</path>
        <oldObjectId>5f9570d6424963490434156b331b5f9772b6f271</oldObjectId>
      </diff>
    </response>

**Perform a diff with a path filter**

::

    $ curl -v "http://localhost:8182/repos/repo1/diff?oldRefSpec=HEAD~3&newRefSpec=HEAD~1&pathFilter=points/d06e56902" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <diff>
        <changeType>ADDED</changeType>
        <newPath>points/d06e56902</newPath>
        <newObjectId>5f9570d6424963490434156b331b5f9772b6f271</newObjectId>
        <path/>
        <oldObjectId>0000000000000000000000000000000000000000</oldObjectId>
      </diff>
    </response>

**Get paged results of a diff**

::

    $ curl -v "http://localhost:8182/repos/repo1/diff?oldRefSpec=HEAD~2&page=0&show=1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <diff>
        <changeType>MODIFIED</changeType>
        <newPath>points/b38e3abb1</newPath>
        <newObjectId>568e38e7b18e64a027342fe1046b1bb371eac7c7</newObjectId>
        <path>points/b38e3abb1</path>
        <oldObjectId>ed0a1c03414c3ae1e56c98fa0ca4613fd446128f</oldObjectId>
      </diff>
      <nextPage>true</nextPage>
    </response>


.. _command_featurediff:

FeatureDiff
===========

Returns the list of attributes for that feature with the before and after values, the changetype, and, if it is the geometry, it returns the CRS with it.

::

    GET /repos/<repo>/featurediff?path=<pathToFeature>[&oldTreeish=<branchOrCommit>][&newTreeish=<branchOrCommit>][&all=<true|false>]
    
Parameters
----------

**path:**
Mandatory.  The path to the feature to show changes on.

**oldTreeish:**
Optional.  The commit or branch from which to get the old version of the feature.  If not specified, ``newTreeish`` must be specified and the diff will show all attributes as added.

**newTreeish:**
Optional.  The commit or branch from which to get the new version of the feature.  If not specified, ``oldTreeish`` must be specified and the diff will show all attributes as deleted.

**all:**
Optional.  If specified as ``true``, all attributes will be returned instead of only the ones that were changed between the two versions.

Examples
--------

**Show the changes the last commit made to a feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/featurediff?path=points/d06e56902&oldTreeish=HEAD~1&newTreeish=HEAD" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <diff>
        <attributename>num_attr</attributename>
        <changetype>MODIFIED</changetype>
        <oldvalue>32</oldvalue>
        <newvalue>34</newvalue>
      </diff>
    </response>

**Show all attributes of a feature diff**

::

    $ curl -v "http://localhost:8182/repos/repo1/featurediff?path=points/d06e56902&oldTreeish=HEAD~1&newTreeish=HEAD&all=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <diff>
        <attributename>str_attr</attributename>
        <changetype>NO_CHANGE</changetype>
        <oldvalue>Point 2</oldvalue>
      </diff>
      <diff>
        <attributename>num_attr</attributename>
        <changetype>MODIFIED</changetype>
        <oldvalue>32</oldvalue>
        <newvalue>34</newvalue>
      </diff>
      <diff>
        <geometry>true</geometry>
        <crs>EPSG:4326</crs>
        <attributename>the_geom</attributename>
        <changetype>NO_CHANGE</changetype>
        <oldvalue>POINT (-60.948837209302326 -11.134883720930318)</oldvalue>
      </diff>
    </response>


.. _command_fetch:

Fetch
=====

Fetch updates from a remote repository. Returns the remote that was fetched from, and the branches that were updated.

::

    GET /repos/<repo>/fetch[?remote=<remoteName>][&all=<true|false>][&prune=<true|false>]

Parameters
----------

**remote:**
Optional.  The name of the remote to fetch changes from.  If not specified, it will try to fetch from the ``origin`` remote.

**all:**
Optional.  If specified as ``true``, the command will fetch changes from all remotes.

**prune:**
Optional.  If specified as ``true``, any remote tracking branches that no longer exist remotely will be pruned locally.

Examples
--------

**Fetch from a specific remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/fetch?remote=origin" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Fetch>
        <Remote>
          <remoteURL>http://localhost:8182/repos/repo1_origin</remoteURL>
          <Branch>
            <changeType>CHANGED_REF</changeType>
            <name>master</name>
            <oldValue>3f933a6e8eadf1caece05d5daaf49663f6fd17b8</oldValue>
            <newValue>02b50e0fafe0bb660369bfd491d676737144025b</newValue>
          </Branch>
        </Remote>
      </Fetch>
    </response>
    
    
**Fetch from all remotes**

::

    $ curl -v "http://localhost:8182/repos/repo1/fetch?all=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Fetch>
        <Remote>
          <remoteURL>http://localhost:8182/repos/repo2</remoteURL>
          <Branch>
            <changeType>ADDED_REF</changeType>
            <name>branch1</name>
            <newValue>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</newValue>
          </Branch>
          <Branch>
            <changeType>ADDED_REF</changeType>
            <name>branch2</name>
            <newValue>af8dfd7718f293aa5532ba948249fca274a0aa13</newValue>
          </Branch>
          <Branch>
            <changeType>ADDED_REF</changeType>
            <name>master</name>
            <newValue>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</newValue>
          </Branch>
        </Remote>
      </Fetch>
    </response>

**Prune remote branch that was deleted**

::

    $ curl -v "http://localhost:8182/repos/repo1/fetch?remote=remote1&prune=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Fetch>
        <Remote>
          <remoteURL>http://localhost:8182/repos/repo2</remoteURL>
          <Branch>
            <changeType>REMOVED_REF</changeType>
            <name>branch2</name>
            <oldValue>af8dfd7718f293aa5532ba948249fca274a0aa13</oldValue>
          </Branch>
        </Remote>
      </Fetch>
    </response>


.. _command_getCommitGraph:

GetCommitGraph
==============

The purpose of the GetCommitGraph function is to traverse the entire commit graph. It starts at the specified commitId and works its way down the graph to either the initial commit or the specified depth. Since it traverses the actual commit graph, unlike log, it will display multiple parents and will list every single commit that runs down each parents history.

::

    GET /repos/<repo>/getCommitGraph?commitId=<commitId>[&depth=<traversalDepth>][&page=<pageNumber>][&show=<elementsPerPage>]
    
Parameters
----------

**commitId:**
Mandatory.  The ID of the commit to start traversing from.

**depth:**
Optional.  If specified, the traversal will be limited to given depth.  If set to ``0`` or not specified, the traversal will cover all commits.

**page:**
Optional.  Page number of the results to view.  If the number of commits exceed the value of ``show``, the results will be paged and must be retrieved with multiple requests.  If not specified, the first page of results will be returned.  If there are additional pages, the response will contain a ``nextPage`` element with a value of ``true``.

**show:**
Optional.  Number of commits to show per page.  If not specified, 30 commits will be shown per page.

Examples
--------

**Get the commit graph of a repository**

::

    $ curl -v "http://localhost:8182/repos/repo1/getCommitGraph?commitId=e227ba80d3bc2b8ee8ebd5e611edf291c2227b84" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commit>
        <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
        ...
      </commit>
      <commit>
        <id>af8dfd7718f293aa5532ba948249fca274a0aa13</id>
        ...
      </commit>
      <commit>
        <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
        ...
      </commit>
      <commit>
        <id>71140aa1439d74312165ca16fc852d5138bea5e7</id>
        ...
      </commit>
    </response>

**Get the commit graph of a repository, limiting depth**

::

    $ curl -v "http://localhost:8182/repos/repo1/getCommitGraph?commitId=e227ba80d3bc2b8ee8ebd5e611edf291c2227b84&depth=2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commit>
        <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
        ..
      </commit>
      <commit>
        <id>af8dfd7718f293aa5532ba948249fca274a0aa13</id>
        ..
      </commit>
    </response>


.. _command_log:

Log
===

Returns the history of the repository as a list of commits. 

::

    GET /repos/<repo>/log[?path=<path>][&limit=<limit>][&offset=<offset>][&since=<commitId>][&until=<commitId>][&sinceTime=<timestamp>][&untilTime=<timestamp>][&firstParentOnly=<true|false>][&countChanges=<true|false>][&returnRange=<true|false>][&summary=<true|false>][&page=<pageNumber>][&show=<elementsPerPage>]
    
Parameters
----------

**path:**
Optional.  Can be specified multiple times.  If specified, only show commits in which the given path(s) were affected.

**limit:**
Optional.  If specified, the log will stop after the given number of commits have been traversed.

**offset:**
Optional.  If specified, the log will skip the given number of commits before beginning.

**since:**
Optional.  If specified, only commits that occurred after the given commit ID will be logged. This excludes the ``since`` commit.

**until:**
Optional.  If specified, only commits that occurred before the given commit ID will be logged.  This includes the ``until`` commit.

**sinceTime:**
Optional.  If specified, only commits that occurred after the given timestamp will be logged.

**untilTime:**
Optional.  If specified, only commits that occurred before the given timestamp will be logged.

**firstParentOnly:**
Optional.  If specified as ``true``, commits will be listed linearly with only the first parent of any commit with multiple parents.  Otherwise, commits will be listed chronologically.

**countChanges:**
Optional.  If specified as ``true``, the log will calculate and return the number of adds, modifies, and deletes for each commit. 

**returnRange:**
Optional.  If specified as ``true``, only the first and last commit in the log's range will be returned, as well as a count of the commits in the range between them.

**summary:**
Optional.  If specified as ``true`` and the output format is specified as ``CSV``, it prompts for download a summary file of changes for each commit in CSV format.  When using ``summary``, a feature type ``path`` must be specified.

**page:**
Optional.  Page number of the results to view.  If the number of commits exceed the value of ``show``, the results will be paged and must be retrieved with multiple requests.  If not specified, the first page of results will be returned.  If there are additional pages, the response will contain a ``nextPage`` element with a value of ``true``.

**show:**
Optional.  Number of commits to show per page.  If not specified, 30 commits will be shown per page.

Examples
--------

**Get the log of repository**

::

    $ curl -v "http://localhost:8182/repos/repo1/log" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml    
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commit>
        <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
        ...
      </commit>
      <commit>
        <id>af8dfd7718f293aa5532ba948249fca274a0aa13</id>
        ...
      </commit>
      <commit>
        <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
        ...
      </commit>
      <commit>
        <id>71140aa1439d74312165ca16fc852d5138bea5e7</id>
        ...
      </commit>
    </response>

**Count the changes for each commit between two commit IDs**

::

    $ curl -v "http://localhost:8182/repos/repo1/log?countChanges=true&since=4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a&until=e227ba80d3bc2b8ee8ebd5e611edf291c2227b84" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml  
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <commit>
        <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
        ...
        <adds>0</adds>
        <modifies>1</modifies>
        <removes>0</removes>
      </commit>
      <commit>
        <id>af8dfd7718f293aa5532ba948249fca274a0aa13</id>
        ...
        <adds>0</adds>
        <modifies>1</modifies>
        <removes>0</removes>
      </commit>
    </response>
    
**Get the log summary as a CSV file**

::

    $ curl -v "http://localhost:8182/repos/repo1/log?summary=true&output_format=csv&path=points"
    < HTTP/1.1 200 OK
    < Content-Type: text/csv
    ChangeType,FeatureId,CommitId,Parent CommitIds,Author Name,Author Email,Author Commit Time,Committer Name,Committer Email,Committer Commit Time,Commit Message,the_geom,str_attr,num_attr
    MODIFIED,points/d06e56902 -> points/d06e56902,e227ba80d3bc2b8ee8ebd5e611edf291c2227b84,af8dfd7718f293aa5532ba948249fca274a0aa13,Test User,example@geogig.org,09/27/2017 22:09:20 EDT,GeoGig Server,server@geogig.org,09/27/2017 22:09:20 EDT,"modified points/d06e56902",POINT (-60.948837209302326 -11.134883720930318),Point 2,34
    MODIFIED,points/b38e3abb1 -> points/b38e3abb1,af8dfd7718f293aa5532ba948249fca274a0aa13,4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a,Test User,example@geogig.org,09/27/2017 22:08:45 EDT,GeoGig Server,server@geogig.org,09/27/2017 22:08:45 EDT,"modified points/b38e3abb1",POINT (-101.67906976744186 -19.339534883721),Point 1,15
    ADDED,points/b38e3abb1,4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a,71140aa1439d74312165ca16fc852d5138bea5e7,Test User,example@geogig.org,09/27/2017 22:06:53 EDT,GeoGig Server,server@geogig.org,09/27/2017 22:06:53 EDT,"added points/b38e3abb1 and points/d06e56902",POINT (-99.04186046511626 -29.88837209302335),Point 1,15
    ADDED,points/d06e56902,4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a,71140aa1439d74312165ca16fc852d5138bea5e7,Test User,example@geogig.org,09/27/2017 22:06:53 EDT,GeoGig Server,server@geogig.org,09/27/2017 22:06:53 EDT,"added points/b38e3abb1 and points/d06e56902",POINT (-60.948837209302326 -11.134883720930318),Point 2,32


.. _command_ls-tree:

LsTree
======

Return the details of a GeoGig revision tree and its contents.

::

    GET /repos/<repo>/ls-tree[?path=<reference>][&showTree=<true|false>][&onlyTree=<true|false>][&recursive=<true|false>][&verbose=<true|false>]
    
Parameters
----------

**path:**
Optional.  If specified, start traversing from the given reference.  This can be a branch/commit, path, or both.  If not specified, the current working tree will be used.

**showTree:**
Optional.  If specified as ``true``, tree nodes will be returned in the response in addition to feature nodes.  Otherwise only feature nodes will be returned.

**onlyTree:**
Optional.  If specified as ``true``, only tree nodes will be returned in the response.

**recursive:**
Optional.  If specified as ``true``, the traversal will recurse into subtrees.  Otherwise only the top level nodes will be visited.

**verbose:**
Optional.  If specified as ``true``, the type, medatadata ID, and object ID of each node will be returned.

Examples
--------

**List all feature type trees**

::

    $ curl -v "http://localhost:8182/repos/repo1/ls-tree" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <node>
        <path>points</path>
      </node>
    </response>
    
**List all features in a given feature type tree**

::

    $ curl -v "http://localhost:8182/repos/repo1/ls-tree?path=points&verbose=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <node>
        <path>b38e3abb1</path>
        <metadataId>e4bce1331e8b2b4a59f81f421c77781a2585b686</metadataId>
        <type>feature</type>
        <objectId>568e38e7b18e64a027342fe1046b1bb371eac7c7</objectId>
      </node>
      <node>
        <path>d06e56902</path>
        <metadataId>e4bce1331e8b2b4a59f81f421c77781a2585b686</metadataId>
        <type>feature</type>
        <objectId>ba7db31656fa1abb7e09a0e37ca4c4a9681dbcdb</objectId>
      </node>
    </response>

**List all trees and features in the repository**

::

    $ curl -v "http://localhost:8182/repos/repo1/ls-tree?recursive=true&showTree=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <node>
        <path>points</path>
      </node>
      <node>
        <path>points/b38e3abb1</path>
      </node>
      <node>
        <path>points/d06e56902</path>
      </node>
    </response>


.. _command_merge:

Merge (-T)
==========

Merges a branch or commit into the currently checked out branch.  This operation may return merge conflicts that must be resolved before the merge can be completed (See :ref:`conflict_resolution`).

::

    GET /repos/<repo>/merge?transactionId=<transactionId>&commit=<branchOrCommit>[&noCommit=<true|false>][&authorName=<authorName>][&authorEmail=<authorEmail>]
    
Parameters
----------

**commit:**
Mandatory.  The branch or commit to merge into the currently checked out branch.

**noCommit:**
Optional.  If specified as ``true`` the operation will leave the results of the merge in the staging area without committing them.  The ``commit`` operation can later be used to commit the merge.

**authorName:**
Optional.  If specified, the provided author name will be used for the merge commit.  Otherwise the committer name will be used.

**authorEmail:**
Optional.  If specified, the provided author email will be used for the merge commit.  Otherwise the committer email will be used.

Examples
--------

**Merge a branch**

::

    $ curl -v "http://localhost:8182/repos/repo1/merge?transactionId=8d3a7b4d-eb58-4382-a586-844b00554246&commit=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>fb4a412f36dbbadac3b0edcff947addfe02a6970</ours>
        <theirs>4951d2455501ead23e62eec53020002943a4a42d</theirs>
        <ancestor>af8dfd7718f293aa5532ba948249fca274a0aa13</ancestor>
        <mergedCommit>5c8b1c422c6f18b1b21c46b17064770ed8b8345b</mergedCommit>
      </Merge>
    </response>

**Merge a branch with conflicts**

::

    $ curl -v "http://localhost:8182/repos/repo1/merge?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&commit=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>fb4a412f36dbbadac3b0edcff947addfe02a6970</ours>
        <theirs>1fbe206f8034a23da88a05ff8b5a4df2cfe1f3f4</theirs>
        <ancestor>af8dfd7718f293aa5532ba948249fca274a0aa13</ancestor>
        <conflicts>1</conflicts>
        <Feature>
          <change>MODIFIED</change>
          <id>points/b38e3abb1</id>
          <geometry>POINT (-101.67906976744186 -19.339534883721)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
        <Feature>
          <change>CONFLICT</change>
          <id>points/d06e56902</id>
          <ourvalue>ee5ebe180982445f29f0dde64ab1e51ad70980c3</ourvalue>
          <theirvalue>d6fd128a83363ef84500822ce61286a4ff91a130</theirvalue>
          <geometry>POINT (-61.42671331549701 -16.869396995266598)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>


.. _command_pull:

Pull (-T)
=========

Pull the changes from a remote branch into a local one.  This operation may return merge conflicts that must be resolved before the merge can be completed (See :ref:`conflict_resolution`).

::

    GET /repos/<repo>/pull?transactionId=<transactionId>[&remoteName=<remoteName>][&all=<true|false>][&ref=<ref>][&authorName=<authorName>][&authorEmail=<authorEmail>]
    
Parameters
----------

**remoteName:**
Optional.  The name of the remote to pull changes from.  If not specified, changes will be pulled from the ``origin`` remote.  This operation may return merge conflicts that must be resolved before the merge can be completed.

**all:**
Optional.  If specified as ``true``, all remotes will be fetched prior to the pull.

**ref:**
Optional.  The ref to pull, in the format ``<remoteref>[:<localref>]`` where ``<remoteref>`` is the remote branch to pull and ``<localref>`` is the local branch to pull to.  If not specified, the remote branch of the currently checked out branch will be pulled.

**authorName:**
Optional.  If specified, the provided author name will be used for the merge commit.  Otherwise the committer name will be used.

**authorEmail:**
Optional.  If specified, the provided author email will be used for the merge commit.  Otherwise the committer email will be used.

Examples
--------

**Pull changes from the origin remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/pull?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&remoteName=origin&ref=master:master" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Pull>
        <Fetch/>
        <Remote>http://localhost:8182/repos/repo1_origin</Remote>
        <Ref>master</Ref>
        <Added>1</Added>
        <Modified>0</Modified>
        <Removed>0</Removed>
        <Merge>
          <ours>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ours>
          <theirs>02b50e0fafe0bb660369bfd491d676737144025b</theirs>
          <ancestor>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ancestor>
          <mergedCommit>02b50e0fafe0bb660369bfd491d676737144025b</mergedCommit>
        </Merge>
      </Pull>
    </response>

**Pull changes with conflicts**

::

    $ curl -v "http://localhost:8182/repos/repo1/pull?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&remoteName=remote1&ref=master:master" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>fb4a412f36dbbadac3b0edcff947addfe02a6970</ours>
        <theirs>f231fca23b59b7b690689e323aacc7472986a2fa</theirs>
        <ancestor>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ancestor>
        <conflicts>1</conflicts>
        <Feature>
          <change>CONFLICT</change>
          <id>points/d06e56902</id>
          <ourvalue>ee5ebe180982445f29f0dde64ab1e51ad70980c3</ourvalue>
          <theirvalue>974aac999492d92d56f32f64a42d6cc9f159ed8b</theirvalue>
          <geometry>POINT (-61.42671331549701 -16.869396995266598)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>


.. _command_push:

Push
====

Push local changes to a remote repository.  If the remote branch has changes that are not on the local branch being pushed, the push will fail.  Those changes should be pulled into the local branch before pushing to avoid loss of data.

::

    GET /repos/<repo>/push[?remoteName=<remoteName>][&ref=<ref>][&all=<true|false>]

Parameters
----------

**remoteName:**
Optional.  The name of the remote to push to.  If not specified, changes will be pushed to the ``origin`` remote.

**ref:**
Optional.  The ref to push, in the format ``<localref>[:<remoteref>]`` where ``<localref>`` is the local branch to push and ``<remoteref>`` is the remote branch to push to.  If not specified, currently checked out branch will be pushed to the branch of the same name or the one that it is tracking in the remote.

**all:**
Optional.  If specified as ``true``, all branches will be pushed to the remote.

Examples
--------

**Push a branch to the origin remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/push" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Push>Success</Push>
      <dataPushed>true</dataPushed>
    </response>

**Push all branches to a remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/push?remoteName=remote1&all=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Push>Success</Push>
      <dataPushed>true</dataPushed>
    </response>


.. _command_rebuildgraph:

RebuildGraph
============

Rebuilds the graph database and returns any graph elements that were repaired.

::

    GET /repos/<repo>/rebuildgraph[?quiet=true]
    
Parameters
----------

**quiet:**
Optional.  If specified as ``true``, only the number of updated graph elements will be returned.

Examples
--------

**Rebuild the graph**

::

    $ curl -v "http://localhost:8182/repos/repo1/rebuildgraph" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml <?xml version="1.0"?>
    <response>
      <success>true</success>
      <RebuildGraph>
        <updatedGraphElements>3</updatedGraphElements>
        <UpdatedObject>
          <ref>af8dfd7718f293aa5532ba948249fca274a0aa13</ref>
        </UpdatedObject>
        <UpdatedObject>
          <ref>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</ref>
        </UpdatedObject>
        <UpdatedObject>
          <ref>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ref>
        </UpdatedObject>
      </RebuildGraph>
    </response>


.. _command_refparse:

RefParse
========

Parses a ref and returns the ref name and object ID.  If it was a symbolic ref, it returns the target as well.

::

    GET /repos/<repo>/refparse?name=<ref>

Parameters
----------

**name:**
Mandatory.  The branch, tag, or symbolic reference to parse.

Examples
--------

**Parse a symbolic ref**

::

    $ curl -v "http://localhost:8182/repos/repo1/refparse?name=HEAD" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Ref>
        <name>HEAD</name>
        <objectId>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</objectId>
        <target>refs/heads/master</target>
      </Ref>
    </response>

**Parse a ref**

::

    $ curl -v "http://localhost:8182/repos/repo1/refparse?name=master" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Ref>
        <name>refs/heads/master</name>
        <objectId>fb4a412f36dbbadac3b0edcff947addfe02a6970</objectId>
      </Ref>
    </response>


.. _command_remote:

Remote
======

List, add, update, remove, or ping remotes from the repository.

::

    GET /repos/<repo>/remote?list=true[&verbose=<true|false>]
    GET /repos/<repo>/remote?remoteName=<name>&remoteURL=<url>[&username=<name>][&password=<password>]
    GET /repos/<repo>/remote?update=true&remoteName=<name>&remoteURL=<url>[&newName=<name>][&username=<name>][&password=<password>]
    GET /repos/<repo>?remove=true&remoteName=<name>
    GET /repos/<repo>?ping=true&remoteName=<name>
    
Parameters
----------

**list:**
Optional.  If specified as ``true``, the names of all remotes will be returned.

**verbose:**
Optional.  Only valid when ``list`` is ``true``.  If specified as ``true``, more information will be returned for each remote.

**remove:**
Optional.  If specified as ``true``, the specified remote will be removed.

**update:**
Optional.  If specified as ``true``, the specified remote will be updated.

**ping:**
Optional.  If specified as ``true``, the specified remote will be pinged.

**remoteName:**
Mandatory unless ``list`` is specified as ``true``.  The name of the remote to perform operations on.

**remoteURL:**
Mandatory for adding and updating remotes.  The URL of the remote to add or update.

**newName:**
Optional.  Only valid when ``update`` is ``true``.  If specified, the specified remote will be renamed to the new name.

**username:**
Optional.  The username to use when connecting to the remote.

**password:**
Optional.  The password to use when connecting to the remote.

Examples
--------

**List remotes**

::

    $ curl -v "http://localhost:8182/repos/repo1/remote?list=true&verbose=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Remote>
        <name>origin</name>
        <url>http://localhost:8182/repos/repo1_origin</url>
      </Remote>
      <Remote>
        <name>remote2</name>
        <url>http://localhost:8182/repos/repo3</url>
      </Remote>
      <Remote>
        <name>remote1</name>
        <url>http://localhost:8182/repos/repo2</url>
      </Remote>
    </response>

**Add a new remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/remote?remoteName=remote1&remoteURL=http://localhost:8182/repos/repo2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <name>remote1</name>
    </response>

**Rename a remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/remote?update=true&remoteName=remote2&remoteURL=http://localhost:8182/repos/repo3&newName=remote2_renamed" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <name>remote2_renamed</name>
    </response>

**Remove a remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/remote?remove=true&remoteName=remote2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <name>remote2</name>
    </response>

**Ping a remote**

::

    $ curl -v "http://localhost:8182/repos/repo1/remote?ping=true&remoteName=remote1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <ping>
        <success>true</success>
      </ping>
    </response>


.. _command_remove:

Remove (-T)
===========

Remove a feature or tree from the staging area.  The ``commit`` endpoint should be called to commit the removal to the transaction.

::

    GET /repos/<repo>/remove?transactionId=<transactionId>&path=<pathToRemove>[&recursive=<true|false>]

Parameters
----------

**path:**
Mandatory.  The path of the feature or tree to remove.  If removing a tree, ``recursive`` must be set to ``true``.

**recursive:**
Optional.  If specified as ``true``, the tree and all features in it will be removed.

Examples
--------

**Remove a feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/remove?transactionId=9c0be77c-8e85-4554-bf4a-3b869fc4a7b6&path=points/d06e56902" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Deleted>points/d06e56902</Deleted>
    </response>

**Remove a feature type tree**

::

    $ curl -v "http://localhost:8182/repos/repo1/remove?transactionId=9c0be77c-8e85-4554-bf4a-3b869fc4a7b6&path=points&recursive=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Deleted>points</Deleted>
    </response>


.. _command_reportMergeScenario:

ReportMergeScenario
===================

Reports conflicts between changes introduced by two different histories. Given a commit and another reference commit, it returns the set of changes from the common ancestor to the first commit, classified according to whether they can or not be safely applied onto the reference commit. Changes that will have no effect on the target commit are not included as unconflicted.  This endpoint does not leave the repository in a conflicted state and can be used as a preview for merge operations.

::

    GET /repos/<repo>/reportMergeScenario?ourCommit=<branchOrCommit>&theirCommit=<branchOrCommit>[&page=<pageNumber>][&elementsPerPage=<elementsPerPage>]
    
Parameters
----------

**ourCommit:**
Mandatory.  The branch or commit to merge changes into.

**theirCommit:**
Mandatory.  The branch or commit that contains changes that need to be merged.

**page:**
Optional.  Page number of the results to view.  If the number of conflicts exceed the value of ``elementsPerPage``, the results will be paged and must be retrieved with multiple requests.  If not specified, the first page of results will be returned.  If there are additional pages, the response will contain an ``additionalChanges`` element with a value of ``true``.

**elementsPerPage:**
Optional.  Number of changes to show per page.  If not specified, 1000 changes will be shown per page.

Examples
--------

**Report a merge scenario**

::

    $ curl -v "http://localhost:8182/repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <Feature>
          <change>MODIFIED</change>
          <id>points/b38e3abb1</id>
          <geometry>POINT (-101.67906976744186 -19.339534883721)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>
    
**Report a merge scenario with conflicts**

::

    $ curl -v "http://localhost:8182/repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <Feature>
          <change>MODIFIED</change>
          <id>points/b38e3abb1</id>
          <geometry>POINT (-101.67906976744186 -19.339534883721)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
        <Feature>
          <change>CONFLICT</change>
          <id>points/d06e56902</id>
          <ourvalue>ee5ebe180982445f29f0dde64ab1e51ad70980c3</ourvalue>
          <theirvalue>d6fd128a83363ef84500822ce61286a4ff91a130</theirvalue>
          <geometry>POINT (-61.42671331549701 -16.869396995266598)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>


.. _command_resolveconflict:

ResolveConflict (-T)
====================

Resolve a conflict at the provided path with the provided feature object ID.  This can be used in conjunction with the output response of a ``MergeFeature`` request.

::

    GET /repos/<repo>/resolveconflict?transactionId=<transactionId>&path=<pathToFeature>&objectid=<objectId>

Parameters
----------

**path:**
Mandatory.  The path to the feature to resolve the conflict for.

**objectid:**
Mandatory.  The object ID of the feature to resolve the conflict with.

Examples
--------

**Resolve a conflicted feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/resolveconflict?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580&path=points/b38e3abb1&objectid=3b1f12f33d00676533d113cdee5494b82f383e46" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Add>Success</Add>
    </response>


.. _command_revertfeature:

RevertFeature (-T)
==================

Revert the changes made to a single feature as a new commit.  This is done by finding all changes made between two commits to a single feature, creating a new commit based off of the later commit, and then merging that commit into the currently checked out branch.  This is done so that any changes made to the feature after the later commit are not lost.  Because of this, the operation may return merge conflicts that must be resolved before the merge can be completed (See :ref:`conflict_resolution`).

::

    GET /repos/<repo>/revertfeature?transactionId=<transactionId>&oldCommitId=<commitId>&newCommitId=<commitId>&path=<pathToFeature>[&commitMessage=<message>][&mergeMessage=<message>][&authorName=<authorName>][&authorEmail=<authorEmail>]
    
Parameters
----------

**path:**
Mandatory.  The path of the feature to revert.

**oldCommitId:**
Mandatory.  The commit that contains the version of the feature to revert to.

**newCommitId:**
Mandatory.  The commit that contains the version of the feature with changes that need to be reverted.

**commitMessage:**
Optional.  The message to use for the revert commit.

**mergeMessage:**
Optional.  The message to use for the merge of the revert commit into the currently checked out branch.

**authorName:**
Optional.  If specified, the provided author name will be used for the commits.  Otherwise the committer name will be used.

**authorEmail:**
Optional.  If specified, the provided author email will be used for the commits.  Otherwise the committer email will be used.

Examples
--------

**Revert changes made to a feature**

::

    $ curl -v "http://localhost:8182/repos/repo1/revertfeature?transactionId=7069620a-d32f-4c04-9c38-12176c2166d9&oldCommitId=af8dfd7718f293aa5532ba948249fca274a0aa13&newCommitId=e227ba80d3bc2b8ee8ebd5e611edf291c2227b84&path=points/d06e56902&commitMessage=Undo" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ours>
        <theirs>21efb042e40278a2ebc786155282bd9963e71b04</theirs>
        <ancestor>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ancestor>
        <mergedCommit>21efb042e40278a2ebc786155282bd9963e71b04</mergedCommit>
      </Merge>
    </response>
    
**Revert changes made to a feature, with conflicts**

::

    $ curl -v "http://localhost:8182/repos/repo1/revertfeature?transactionId=80f328f2-c202-416f-8ff0-f3110f329e6b&oldCommitId=71140aa1439d74312165ca16fc852d5138bea5e7&newCommitId=4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a&path=points/d06e56902&commitMessage=UndoAdd" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</ours>
        <theirs>a7950a08cc4a28fa3b9bd9b427d6a8c6dcde1c2e</theirs>
        <ancestor>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</ancestor>
        <conflicts>1</conflicts>
        <Feature>
          <change>CONFLICT</change>
          <id>points/d06e56902</id>
          <ourvalue>ba7db31656fa1abb7e09a0e37ca4c4a9681dbcdb</ourvalue>
          <theirvalue>0000000000000000000000000000000000000000</theirvalue>
          <geometry>POINT (-60.948837209302326 -11.134883720930318)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>


.. _command_statistics:

Statistics
==========

Returns repository statistics such as the number of commits, the number and type of changes made, as well as information about the authors that have contributed to a branch.

::

    GET /repos/<repo>/statistics[?path=<pathFilter>][&since=<timestamp>][&branch=<branchOrCommit>]
    
Parameters
----------

**path:**
Optional.  If specified, only commits that have affected the given path will be considered in the statistics.

**since:**
Optional.  If specified, only commits that occurred after the given timestamp will be considered in the statistics.

**branch:**
Optional.  The branch to get statistics on.  If not specified, statistics will be computed for the currently checked out branch.

Examples
--------

**Get the statistics of a branch**

::

    $ curl -v "http://localhost:8182/repos/repo1/statistics?branch=branch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Statistics>
        <FeatureTypes>
          <FeatureType>
            <name>points</name>
            <numFeatures>2</numFeatures>
          </FeatureType>
        </FeatureTypes>
        <latestCommit>
          <id>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</id>
          ...
        </latestCommit>
        <firstCommit>
          <id>71140aa1439d74312165ca16fc852d5138bea5e7</id>
          ...
        </firstCommit>
        <totalCommits>2</totalCommits>
        <Authors>
          <Author>
            <name>GeoGig Server</name>
            <email>server@geogig.org</email>
          </Author>
          <Author>
            <name>Test User</name>
            <email>example@geogig.org</email>
          </Author>
          <totalAuthors>2</totalAuthors>
        </Authors>
      </Statistics>
    </response>

**Get the statistics of a feature tree**

::

    $ curl -v "http://localhost:8182/repos/repo1/statistics?path=points" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Statistics>
        <FeatureTypes>
          <FeatureType>
            <name>points</name>
            <numFeatures>2</numFeatures>
          </FeatureType>
        </FeatureTypes>
        <latestCommit>
          <id>e227ba80d3bc2b8ee8ebd5e611edf291c2227b84</id>
          ...
        </latestCommit>
        <firstCommit>
          <id>71140aa1439d74312165ca16fc852d5138bea5e7</id>
          ...
        </firstCommit>
        <totalCommits>4</totalCommits>
        <Authors>
          <Author>
            <name>GeoGig Server</name>
            <email>server@geogig.org</email>
          </Author>
          <Author>
            <name>Test User</name>
            <email>example@geogig.org</email>
          </Author>
          <totalAuthors>2</totalAuthors>
        </Authors>
      </Statistics>
    </response>


.. _command_status:

Status
======

Returns the branch name of the currently checked out branch as well as a list of the staged, unstaged, and unmerged features.

::

    GET /repos/<repo>/status[?limit=<limit>][&offset=<offset>]

Parameters
----------

**limit:**
Optional.  The number of staged and unstaged changes to show.  If not specified, up to 50 changes will be shown.

**offset:**
Optional.  The number of changes to skip before listing changes.  Defaults to 0.

Examples
--------

**Show the status of the repository**

::

    $ curl -v "http://localhost:8182/repos/repo1/status?transactionId=80f328f2-c202-416f-8ff0-f3110f329e6b" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml 
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <header>
        <branch>master</branch>
      </header>
      <staged>
        <changeType>REMOVED</changeType>
        <newPath/>
        <newObjectId>0000000000000000000000000000000000000000</newObjectId>
        <path>points/b38e3abb1</path>
        <oldObjectId>568e38e7b18e64a027342fe1046b1bb371eac7c7</oldObjectId>
      </staged>
      <unmerged>
        <changeType>CONFLICT</changeType>
        <path>points/d06e56902</path>
        <ours>ba7db31656fa1abb7e09a0e37ca4c4a9681dbcdb</ours>
        <theirs>0000000000000000000000000000000000000000</theirs>
        <ancestor>5f9570d6424963490434156b331b5f9772b6f271</ancestor>
      </unmerged>
    </response>


.. _command_tag:

Tag
===

List, create, or delete tags from the repository.

::

    GET /repos/<repo>/tag
    PUT /repos/<repo>/tag?name=<tagName>&message=<tagMessage>&commit=<ref>
    DELETE /repos/<repo>/tag?name=<tagName>

Parameters
----------

**name:**
Mandatory for creating and deleting tags.  The name of the tag to create or delete.

**message:**
Mandatory for creating a tag.  The message of the tag to create.

**commit:**
Mandatory for creating a tag.  The branch or commit that the tag should point to.

Examples
--------

**List all tags**

::

    $ curl -v "http://localhost:8182/repos/repo1/tag" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Tag>
        <id>b6dbb92f7f96e1dea36c2c834e53cd602e5ef6a8</id>
        <commitid>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</commitid>
        <name>tag1</name>
        <message>MyTagMessage</message>
        <tagger>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506567453031</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </tagger>
      </Tag>
      <Tag>
        <id>d0502b03f092d3c38009fb0585e283dcaa9f6339</id>
        <commitid>af8dfd7718f293aa5532ba948249fca274a0aa13</commitid>
        <name>tag2</name>
        <message>MySecondTag</message>
        <tagger>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506572141806</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </tagger>
      </Tag>
    </response>

**Create a new tag**

::

    $ curl -X POST -v "http://localhost:8182/repos/repo1/tag?name=tag1&message=MyTagMessage&commit=HEAD~2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Tag>
        <id>b6dbb92f7f96e1dea36c2c834e53cd602e5ef6a8</id>
        <commitid>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</commitid>
        <name>tag1</name>
        <message>MyTagMessage</message>
        <tagger>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506567453031</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </tagger>
      </Tag>
    </response>
    

**Delete a tag**

::

    $ curl -X DELETE -v "http://localhost:8182/repos/repo1/tag?name=tag2" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <DeletedTag>
        <id>d0502b03f092d3c38009fb0585e283dcaa9f6339</id>
        <commitid>af8dfd7718f293aa5532ba948249fca274a0aa13</commitid>
        <name>tag2</name>
        <message>MySecondTag</message>
        <tagger>
          <name>Test User</name>
          <email>example@geogig.org</email>
          <timestamp>1506572141806</timestamp>
          <timeZoneOffset>-14400000</timeZoneOffset>
        </tagger>
      </DeletedTag>
    </response>


.. _command_updateref:

UpdateRef
=========

Update or delete a ref in the repository.

::

    GET /repos/<repo>/updateref?name=<refName>&newValue=<value>
    GET /repos/<repo>/updateref?name=<refName>&delete=true

Parameters
----------

**name:**
Mandatory.  The name of the ref to update or delete.

**newValue:**
Mandatory when updating a ref.  The new value to change the ref to.

**delete:**
Mandatory to specify as ``true`` when deleting a ref.

Examples
--------

**Update a ref**

::

    $ curl -v "http://localhost:8182/repos/repo1/updateref?name=branch1&newValue=af8dfd7718f293aa5532ba948249fca274a0aa13" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <ChangedRef>
        <name>refs/heads/branch1</name>
        <objectId>af8dfd7718f293aa5532ba948249fca274a0aa13</objectId>
      </ChangedRef>
    </response>

**Update a symbolic ref**

::

    $ curl -v "http://localhost:8182/repos/repo1/updateref?name=HEAD&newValue=refs%2Fheads%2Fbranch1" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <ChangedRef>
        <name>HEAD</name>
        <objectId>4dbae82f2f4a568ec8cc7a33f0d7be19ff73059a</objectId>
        <target>refs/heads/branch1</target>
      </ChangedRef>
    </response>

**Delete a ref**

::

    $ curl -v "http://localhost:8182/repos/repo1/updateref?name=branch2&delete=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <ChangedRef>
        <name>refs/heads/branch2</name>
        <objectId>af8dfd7718f293aa5532ba948249fca274a0aa13</objectId>
      </ChangedRef>
    </response>


.. _command_version:

Version
=======

Returns all of the information for the running version of GeoGig.

::

    GET /repos/<repo>/version

Parameters
----------

None.

Examples
--------

**Get GeoGig version**

::

    $ curl -v "http://localhost:8182/repos/repo1/version" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <ProjectVersion>...</ProjectVersion>
      <BuildTime>...</BuildTime>
      <BuildUserName>...</BuildUserName>
      <BuildUserEmail>...</BuildUserEmail>
      <GitBranch>...</GitBranch>
      <GitCommitID>...</GitCommitID>
      <GitCommitTime>...</GitCommitTime>
      <GitCommitAuthorName>...</GitCommitAuthorName>
      <GitCommitAuthorEmail>...</GitCommitAuthorEmail>
      <GitCommitMessage>...</GitCommitMessage>
    </response>


Web API: Repo Commands
######################

These commands can be used by using the ``repos/<repo name>/repo/`` endpoint, instead of the standard ``repos/<repo name>/`` endpoint.

 .. note:: The output format for all repo commands is plain text.


.. _command_mergefeature:

MergeFeature
============

This endpoint can be used to merge two features into a new one.  It will return the object ID of the new feature when the operation completes. This endpoint must be accessed by using a ``POST`` request that contains a JSON object to tell GeoGig how to merge the feature.

::

    POST /repos/<repo>/repo/mergefeature
    
Parameters
----------

None.

Examples
--------

The following is an example of the JSON ``POST`` data to merge a feature with three attributes.

.. code-block:: none

   {
     path: 'featureType/feature',
     ours: 'commitId that contains the left feature',
     theirs: 'commitId that contains the right feature',
     merges: {
        attr1: {
            ours: true // use the value from the left feature
        },
        attr2: {
            theirs: true // use the value from the right feature
        },
        attr3: {
            value: 'custom value' // use our own value
        }
     }
   }

Output:

::

    < HTTP/1.1 200 OK
    < Content-Type: text/plain
    568e38e7b18e64a027342fe1046b1bb371eac7c7


.. _command_manifest:

Manifest
========

This endpoint can be used to get a list of all refs in the repository and what they point to.  Similar to the Branch_ command with the list option from above.

::

    GET /repos/<repo>/repo/manifest

Parameters
----------

None.

Examples
--------

**Get the manifest**

::
    
    $ curl -v "http://localhost:8182/repos/repo1/repo/manifest"
    < HTTP/1.1 200 OK
    < Content-Type: text/plain
    HEAD refs/heads/master 4f1c842f9d453817160f4304316b799b1b80aaf8
    refs/heads/branch1 1fbe206f8034a23da88a05ff8b5a4df2cfe1f3f4
    refs/heads/master 4f1c842f9d453817160f4304316b799b1b80aaf8
    refs/tags/tag1 b6dbb92f7f96e1dea36c2c834e53cd602e5ef6a8
