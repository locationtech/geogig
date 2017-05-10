GeoGig Network Synchronization
==============================

For synchronization over network connections, GeoGig defines a protocol layered over HTTP.
This document defines the operations the GeoGig server must provide, and the way that a GeoGig client is expected to use it to achieve synchronization.
In fact, GeoGig synchronization consists of two different types of operation - a "push" in which a client copies history to a server, and a "pull" in which a client copies history from a server.

Server operations
=================

Manifest
--------

The server provides a "manifest" document advertising its named refs and their current commit ids.
Synchronization operations generally begin with the client requesting a manifest from the server in order to assess the differences between their histories.

The manifest resource responds to GET requests at ``repo/manifest`` relative to the GeoGig server and contains a line-oriented textual description of the refs.
Each ref includes its name and commit id (the commit id in hexadecimal notation); a prefix including the symbolic name for the ref may also be included.
The first line should represent the HEAD symbolic ref.

Example
.......

.. code-block:: none

    GET /geogig/repo/manifest

    HEAD /refs/branches/master 00000000000000000000
    /refs/branches/master 00000000000000000000

.. note::
   
    This doesn't seem to account for the case where a symbolic ref does not point at a named branch/tag/whatever.
    What happens in that case?

Existence Check
---------------

The server provides an existence check service to support history negotiation - this walks history backwards (from newer to older commits) and gives information about the lineage of commits that might not be present on the client.
When a client determines that it has content to send to a server or that a server has content that should be fetched, history negotiation helps to narrow down exactly the range of commits that must be transfered.

The existence-check resource responds to POST requests at ``repo/exists``.
The request body should be a JSON document containing two fields:

    * ``have`` a list of object ids representing commits already present on the client. 
               When walking history, the server should consider any commits from the "have" list as stopping points - as these commits are already on the client it already understands their history.
    * ``want`` a list of object ids representing commits whose lineage the client is trying to determine (generally these should be commits that the client does not already have!)

The server responds with a JSON document enumerating the history of the commits in the want list, or an error if the ``want`` list requests commits that the server does not have.
The history is represented as an `adjacency list <http://en.wikipedia.org/wiki/Adjacency_list>`_ with the commit id in a field named ``id`` and an array containing its parents in a field named ``parents``.
The server must include information about the commits listed in the want list, but it is up to the server to determine how many further commits to include.

Example
.......

In the following example, the "want" list conveniently contains only a single commit - which happens to be a direct descendant of the one commit in the "have" list.

.. code-block:: none

   POST /geogig/repo/exists
   {
     "want" : [ "abcdefabcdefabcdef12" ],
     "have" : [ "123456567890345678ab" ]
   }

   { 
     "history" : [
        {
          "id" : "abcdefabcdefabcdef12",
          "parents": ["123456567890345678ab"]
        }
     ]
   }

Bulk Objects
------------

The server provides a bulk objects service for fetching many objects in a single request.
This walks history forward (opposite the existence check resource!) to transfer multiple objects.
By walking history in commit order, we increase robustness - an interrupted session still has high probability of leaving the client with valid commits "connected" to the rest of its history.
A well-implemented client should then be able to resume a transfer without needing to re-fetch the already-sent objects.

The bulk objects service accepts POST requests at ``repo/objects``.
The request body should be a JSON document containing two fields:

    * ``have`` a list of object ids representing commits already present on the client. 
               When walking history, the server should consider any commits from the "have" list as stopping points - as these commits are already on the client it already understands their history.
    * ``want`` a list of object ids representing commits whose lineage the client is trying to determine (generally these should be commits that the client does not already have!)

Note that this is the same format as accepted by the existence check service.

The server responds by streaming history objects directly in binary format.
Specifically, a 20-byte sha1 id is sent, followed by a "raw" binary blob, followed by another 20-byte id, followed by another blob, etc.

While the server SHOULD optimize by avoiding sending objects that are reachable from commits in the "have" list, the client MUST be prepared to handle receiving objects that it already has locally.

.. note::

   Perhaps this stream should include more metadata - knowing how many objects were sent or where boundaries are expected to be would probably be useful for reliability purposes.

Example
.......

.. code-block:: none

   POST /geogig/repo/objects
   { 
     "want": ["abcdefabcdefabcdef12"],
     "have": ["123456567890345678ab"]
   }

   <20 byte id><binary encoded feature><20 byte id><binary encoded tree><20 byte id><binary encoded commit>

Bulk Send
---------

The server provides a bulk send service for uploading many objects in a single request.
As with bulk object retrieval, clients should traverse in commit order.

The bulk send service accepts POST requests at ``repo/send-objects``.
The request body should consist of one or more 20-byte ids, each followed by a binary-encoded history object.
Note this is the same format as produced by the bulk objects resource.

The server responds by sending a 201 Accepted status code, or an HTTP error code as appropriate.

Reference Update
----------------

The server provides a reference update service for modifying the references on the server - adding new named references, removing them, or updating existing ones to change which commits they name.

The reference update service accepts POST requests at ``repo/update-refs``. (NOT YET IMPLEMENTED.)
The request body should be a JSON document containing one field:

    * ``updates`` a list of JSON objects with ``ref``, ``to``, and ``from`` fields identifying the ref name, new value, and old value of all refs to be updated. 
      If the 'to' field contains the special value "00000000000000000000" then the ref should be deleted.
      If the ref does not already exist, the client should use the special value "00000000000000000000" for the 'from' field.

The server responds with a document with a similar ``updates`` field containing the updates that were executed.  Updates may be rejected for any reasons the server deems appropriate, but MUST be rejected in the following situations:

    * if the "from" value of an update object does not correspond to the current value of the ref at the time of update.
    * if the "to" value of the update object corresponds to an object that is not a commit
    * if the "to" value of the update object corresponds to an object that is not known to the server
    * If the "to" value of the update object corresponds to a commit whose full history and content is not known to the server.

