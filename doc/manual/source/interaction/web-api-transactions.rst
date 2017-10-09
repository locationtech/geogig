Web API: Transactions
#####################

Many web API commands have transaction support to allow multiple users to change the state of the repository without compromising stability.  This works by beginning a transaction, which will generate a unique transaction ID.  This ID can then be passed to many web API commands so that all work done by that command will affect only that transaction.  After you are done with everything on the transaction you can then end that transaction to merge those changes into the base repository. Some commands require a transaction to preserve the stability of the repository. Those that require it will have ``(-T)`` next to the name of the command in this doc. To perform a command on a transaction, simply supply the transaction ID via the ``transactionId`` parameter.

BeginTransaction
================

Begins a new transaction, generating a new transaction ID.

::

    GET /repos/<repo>/beginTransaction
    
Parameters
----------

None.

Examples
--------

**Begin a new transaction**

::

    $ curl -v "http://localhost:8182/repos/repo1/beginTransaction" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <Transaction>
            <ID>e6062d14-5b31-46b5-8e83-9d02fdc45504</ID>
        </Transaction>
    </response>


EndTransaction (-T)
===================

Ends the transaction that belongs to the ID provided in the request.   This operation may return merge conflicts that must be resolved before the merge can be completed (See :ref:`conflict_resolution`).

::

    GET /repos/<repo>/endTransaction?transactionId=<transactionId>[&cancel=<true|false>]
    
Parameters
----------

**cancel:**
Optional.  If true, the changes made on the transaction will be discarded.  If not defined, the transaction's changes will be merged into the base repository.

Examples
--------

**End a transaction**

::

    $ curl -v "http://localhost:8182/repos/repo1/endTransaction?transactionId=e6062d14-5b31-46b5-8e83-9d02fdc45504" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <Transaction>
            <ID>e6062d14-5b31-46b5-8e83-9d02fdc45504</ID>
        </Transaction>
    </response>

**End a transaction with conflicts**

::

    $ curl -v "http://localhost:8182/repos/repo1/endTransaction?transactionId=4c3af4a5-e537-40eb-b624-02d62e1d9580" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0"?>
    <response>
      <success>true</success>
      <Merge>
        <ours>fe2b96c6f4b48dfe856c30cc97632025a38bd61c</ours>
        <theirs>ee2d5fede514b8430d3fba6939c88b8d7194dfa4</theirs>
        <ancestor>fb4a412f36dbbadac3b0edcff947addfe02a6970</ancestor>
        <conflicts>1</conflicts>
        <Feature>
          <change>CONFLICT</change>
          <id>points/b38e3abb1</id>
          <ourvalue>3b1f12f33d00676533d113cdee5494b82f383e46</ourvalue>
          <theirvalue>34e78ca7ff6b3c37a4ec43558865e4437c19d5be</theirvalue>
          <geometry>POINT (-101.67906976744186 -19.339534883721)</geometry>
          <crs>EPSG:4326</crs>
        </Feature>
      </Merge>
    </response>
    
.. note:: If there were conflicting changes on the base repository when ending a transaction, this endpoint will return those conflicts.  They must be resolved before attempting to end the transaction again.

**Cancel a transaction**

::

    $ curl -v "http://localhost:8182/repos/repo1/endTransaction?transactionId=e6062d14-5b31-46b5-8e83-9d02fdc45504&cancel=true" | xmllint --format -
    < HTTP/1.1 200 OK
    < Content-Type: application/xml
    <?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <Transaction>
            <ID>e6062d14-5b31-46b5-8e83-9d02fdc45504</ID>
        </Transaction>
    </response>
