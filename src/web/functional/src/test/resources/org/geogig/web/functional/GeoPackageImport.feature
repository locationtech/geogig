@GeopackageSupport
Feature: Import GeoPackage
  The GeoPackage import Web-API allows for uploading a set of layers from a GeoPackage file
  onto a repository snapshot and create a new commit reflecting the imported contents. 
  The GeoPackage file is sent as a POST form arument named "fileUpload". 
  Other URL arguments can be used to control some aspects of the import.
  
  API Spec: POST /repos/<repo>/import?format=gpkg[&add=<true|false>][&alter=<true|false>]
  
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/import?format=gpkg"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
      
  Scenario: Verify missing "format=gpkg" argument issues 400 "Bad request"
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "POST /repos/repo1/import?transactionId={@txId}"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the response xml matches
      """
      <response><success>false</success><error>missing required 'format' parameter</error></response>
      """

  Scenario: Verify unsupported output format argument issues 400 "Bad request"
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "POST /repos/repo1/import?format=badFormat&transactionId={@txId}"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the response xml matches
      """
      <response><success>false</success><error>Unsupported input format: 'badFormat'</error></response>
      """

  Scenario: Verify import to a non existent repository issues 404 "Not found"
    Given There is an empty multirepo server
      And I have a geopackage file @gpkgFile
     When I post @gpkgFile as "fileUpload" to "/repos/badRepo/import?format=gpkg"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"

  Scenario: Import to an empty repository
    Given There is an empty repository named targetRepo
      And I have a geopackage file @gpkgFile
      And I have a transaction as "@txId" on the "targetRepo" repo
     When I post @gpkgFile as "fileUpload" to "/repos/targetRepo/import?format=gpkg&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FINISHED
      And the xml response should contain "/task/result/commit/id"
      And the xml response should contain "/task/result/commit/tree"
      And I end the transaction with id "@txId" on the "targetRepo" repo
      And the targetRepo repository's HEAD should have the following features:
          | Points | Lines | Polygons | 
          |    1   |   1   |    1     | 
          |    2   |   2   |    2     | 
          |    3   |   3   |    3     | 
          
  Scenario: Import an interchange geopackage with fast-forward merge
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
      And I have a transaction as "@txId" on the "repo1" repo
     When I add Points/4 to the geopackage file @gpkgFile
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FINISHED
      And the xml response should contain "/task/result/commit/id"
      And the xml response should contain "/task/result/commit/tree"
      And the xpath "/task/result/commit/message" equals "Imported Geopackage"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's HEAD should have the following features:
          | Points | Lines | Polygons | 
          |    1   |   1   |    1     | 
          |    2   |   2   |    2     | 
          |    3   |   3   |    3     | 
          |    4   |       |          |
      
  Scenario: Import an interchange geopackage with non-conflicting merge
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I add Points/4 to the geopackage file @gpkgFile
      And I remove Points/1 from "repo1"
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FINISHED
      And the xml response should contain "/task/result/commit/id"
      And the xml response should contain "/task/result/commit/tree"
      And the xpath "/task/result/commit/message" equals "Merge: Imported Geopackage"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's HEAD should have the following features:
          | Points | Lines | Polygons | 
          |    2   |   1   |    1     | 
          |    3   |   2   |    2     | 
          |    4   |   3   |    3     | 
      
  Scenario: Import an interchange geopackage with conflicting merge
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I modify the Point features in the geopackage file @gpkgFile
      And I remove Points/1 from "repo1"
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FAILED
      And the xml response should contain "/task/result/Merge/ours"
      And the xml response should contain "/task/result/Merge/theirs"
      And the xml response should contain "/task/result/Merge/ancestor"
      And the xpath "/task/result/Merge/conflicts" equals "1"


#<task><id>1</id><status>FINISHED</status><transactionId>c4da5a9b-5b09-4cb6-9055-e340d02b57ac</transactionId><description>Importing GeoPackage database file.</description><atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="/tasks/1.xml" type="application/xml"/><result><RevCommit><id>a1cde458d0658e096998b740b2eaa7b10796e624</id><treeId>37987a1d4afbf60be906d55576392965654d5d9c</treeId></RevCommit></result></task>
