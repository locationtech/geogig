@GeopackageSupport @GeoPackageImport
Feature: Import GeoPackage
  The GeoPackage import Web-API allows for uploading a set of layers from a GeoPackage file
  onto a repository snapshot and create a new commit reflecting the imported contents. 
  The GeoPackage file is sent as a POST form arument named "fileUpload". 
  Other URL arguments can be used to control some aspects of the import.
  
  API Spec: POST /repos/<repo>/import?format=gpkg[&add=<true|false>][&alter=<true|false>]
  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/import?format=gpkg"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
      
  @Status400
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
      
  @Status400
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
      
  @Status404
  Scenario: Verify import to a non existent repository issues 404 "Not found"
    Given There is an empty multirepo server
      And I have a geopackage file @gpkgFile
     When I post @gpkgFile as "fileUpload" to "/repos/badRepo/import?format=gpkg"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."

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
      And the xpath "/task/result/commit/id/text()" equals "{@ObjectId|targetRepo|@txId|master}"
      And the xml response should contain "/task/result/commit/tree"
      And I end the transaction with id "@txId" on the "targetRepo" repo
      And the targetRepo repository's "HEAD" should have the following features:
          |    Points    |   Lines    |    Polygons     | 
          |    Point.1   |   Line.1   |    Polygon.1    | 
          |    Point.2   |   Line.2   |    Polygon.2    | 
          |    Point.3   |   Line.3   |    Polygon.3    | 
      And I prune the task @taskId
          
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
      And the xpath "/task/result/newCommit/id/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xml response should contain "/task/result/newCommit/tree"
      And the xpath "/task/result/newCommit/message" equals "Imported Geopackage"
      And the xpath "/task/result/importCommit/id/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xml response should contain "/task/result/importCommit/tree"
      And the xpath "/task/result/importCommit/message" equals "Imported Geopackage"
      And the xpath "/task/result/NewFeatures/type/@name" equals "Points"
      And the xml response should contain "/task/result/NewFeatures/type/id/@provided" 1 times
      And the xml response should contain "/task/result/NewFeatures/type/id/@assigned" 1 times
      And I save the response "/task/result/NewFeatures/type/id/@assigned" as "@newFeatureId"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's "HEAD" should have the following features:
          |    Points     |   Lines    |    Polygons     | 
          |    Point.1    |   Line.1   |    Polygon.1    | 
          |    Point.2    |   Line.2   |    Polygon.2    | 
          |    Point.3    |   Line.3   |    Polygon.3    | 
          |{@newFeatureId}|            |                 |
      And I prune the task @taskId
      
  Scenario: Import an interchange geopackage with non-conflicting merge
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I add Points/4 to the geopackage file @gpkgFile
      And I have removed "Point.1" on the "repo1" repo in the "" transaction
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FINISHED
      And the xpath "/task/result/newCommit/id/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xml response should contain "/task/result/newCommit/tree"
      And the xpath "/task/result/newCommit/message" equals "Merge: Imported Geopackage"
      And the xpath "/task/result/importCommit/id/text()" equals "{@ObjectId|repo1|@txId|master^2}"
      And the xml response should contain "/task/result/importCommit/tree"
      And the xpath "/task/result/importCommit/message" equals "Imported Geopackage"
      And the xpath "/task/result/NewFeatures/type/@name" equals "Points"
      And the xml response should contain "/task/result/NewFeatures/type/id/@provided" 1 times
      And the xml response should contain "/task/result/NewFeatures/type/id/@assigned" 1 times
      And I save the response "/task/result/NewFeatures/type/id/@assigned" as "@newFeatureId"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's "HEAD" should have the following features:
          |    Points     |   Lines    |    Polygons     | 
          |    Point.2    |   Line.1   |    Polygon.1    | 
          |    Point.3    |   Line.2   |    Polygon.2    | 
          |{@newFeatureId}|   Line.3   |    Polygon.3    | 
      And I prune the task @taskId
      
  Scenario: Import an interchange geopackage with conflicting merge
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I modify the Point features in the geopackage file @gpkgFile
      And I add Points/4 to the geopackage file @gpkgFile
      And I have removed "Point.1" on the "repo1" repo in the "" transaction
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is an XML async task @taskId
      And the task @taskId description contains "Importing GeoPackage database file."
      And when the task @taskId finishes
     Then the task @taskId status is FAILED
      And the xml response should contain "/task/result/import/importCommit/id"
      And I save the response "/task/result/import/importCommit/id/text()" as "@importCommit"
      And the xml response should contain "/task/result/import/importCommit/tree"
      And the xpath "/task/result/import/importCommit/message" equals "Imported Geopackage"
      And the xpath "/task/result/Merge/ours/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/task/result/Merge/theirs/text()" equals "{@importCommit}"
      And the xpath "/task/result/Merge/ancestor/text()" equals "{@ObjectId|repo1|@txId|master~1}"
      And the xpath "/task/result/import/NewFeatures/type/@name" equals "Points"
      And the xml response should contain "/task/result/import/NewFeatures/type/id/@provided" 1 times
      And the xml response should contain "/task/result/import/NewFeatures/type/id/@assigned" 1 times
      And I save the response "/task/result/import/NewFeatures/type/id/@assigned" as "@newFeatureId"
      And the xpath "/task/result/Merge/conflicts" equals "1"
      And the repo1 repository's "WORK_HEAD" in the @txId transaction should have the following features:
          |    Points     |   Lines    |    Polygons     | 
          |    Point.2    |   Line.1   |    Polygon.1    | 
          |    Point.3    |   Line.2   |    Polygon.2    | 
          |{@newFeatureId}|   Line.3   |    Polygon.3    | 
      And I prune the task @taskId


#<task><id>1</id><status>FINISHED</status><transactionId>c4da5a9b-5b09-4cb6-9055-e340d02b57ac</transactionId><description>Importing GeoPackage database file.</description><atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="/tasks/1.xml" type="application/xml"/><result><RevCommit><id>a1cde458d0658e096998b740b2eaa7b10796e624</id><treeId>37987a1d4afbf60be906d55576392965654d5d9c</treeId></RevCommit></result></task>

# JSON tests
  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed", JSON requested response
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/import.json?format=gpkg"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
      
  @Status400
  Scenario: Verify missing "format=gpkg" argument issues 400 "Bad request", JSON requested response
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "POST /repos/repo1/import.json?transactionId={@txId}"
     Then the response status should be '400'
      And the response ContentType should be "application/json"
      And the json object "response.success" equals "false"
      And the json object "response.error" equals "missing required 'format' parameter"
      
  @Status400
  Scenario: Verify unsupported output format argument issues 400 "Bad request", JSON requested response
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "POST /repos/repo1/import.json?format=badFormat&transactionId={@txId}"
     Then the response status should be '400'
      And the response ContentType should be "application/json"
      And the json object "response.success" equals "false"
      And the json object "response.error" equals "Unsupported input format: 'badFormat'"
      
  @Status404
  Scenario: Verify import to a non existent repository issues 404 "Not found", JSON requested response
    Given There is an empty multirepo server
      And I have a geopackage file @gpkgFile
     When I post @gpkgFile as "fileUpload" to "/repos/badRepo/import.json?format=gpkg"
     Then the response status should be '404'
      And the response ContentType should be "application/json"
      And the json object "response.success" equals "false"
      And the json object "response.error" equals "Repository not found."

  Scenario: Import to an empty repository, JSON requested response
    Given There is an empty repository named targetRepo
      And I have a geopackage file @gpkgFile
      And I have a transaction as "@txId" on the "targetRepo" repo
     When I post @gpkgFile as "fileUpload" to "/repos/targetRepo/import.json?format=gpkg&transactionId={@txId}"
     Then the response status should be '200'
      And the response is a JSON async task @taskId
      And the JSON task @taskId description contains "Importing GeoPackage database file."
      And when the JSON task @taskId finishes
     Then the JSON task @taskId status is FINISHED
      And the json object "task.result.commit.id" equals "{@ObjectId|targetRepo|@txId|master}"
      And the json response "task.result.commit" should contain "tree"
      And I end the transaction with id "@txId" on the "targetRepo" repo
      And the targetRepo repository's "HEAD" should have the following features:
          |    Points    |   Lines    |    Polygons     |
          |    Point.1   |   Line.1   |    Polygon.1    |
          |    Point.2   |   Line.2   |    Polygon.2    |
          |    Point.3   |   Line.3   |    Polygon.3    |
      And I prune the task @taskId

  Scenario: Import an interchange geopackage with fast-forward merge, JSON requested response
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
      And I have a transaction as "@txId" on the "repo1" repo
     When I add Points/4 to the geopackage file @gpkgFile
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import.json?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is a JSON async task @taskId
      And the JSON task @taskId description contains "Importing GeoPackage database file."
      And when the JSON task @taskId finishes
     Then the JSON task @taskId status is FINISHED
      And the json object "task.result.newCommit.id" equals "{@ObjectId|repo1|@txId|master}"
      And the json response "task.result.newCommit" should contain "tree"
      And the json object "task.result.newCommit.message" equals "Imported Geopackage"
      And the json object "task.result.importCommit.id" equals "{@ObjectId|repo1|@txId|master}"
      And the json response "task.result.importCommit" should contain "tree"
      And the json object "task.result.importCommit.message" equals "Imported Geopackage"
      And the json object "task.result.NewFeatures.type[0].name" equals "Points"
      And the json response "task.result.NewFeatures.type[0].id[0].provided" should contain ""
      And the json response "task.result.NewFeatures.type[0].id[0].assigned" should contain ""
      And I save the json response "task.result.NewFeatures.type[0].id[0].assigned" as "@newFeatureId"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's "HEAD" should have the following features:
          |    Points     |   Lines    |    Polygons     |
          |    Point.1    |   Line.1   |    Polygon.1    |
          |    Point.2    |   Line.2   |    Polygon.2    |
          |    Point.3    |   Line.3   |    Polygon.3    |
          |{@newFeatureId}|            |                 |
      And I prune the task @taskId

  Scenario: Import an interchange geopackage with non-conflicting merge, JSON requested response
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I add Points/4 to the geopackage file @gpkgFile
      And I have removed "Point.1" on the "repo1" repo in the "" transaction
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import.json?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is a JSON async task @taskId
      And the JSON task @taskId description contains "Importing GeoPackage database file."
      And when the JSON task @taskId finishes
     Then the JSON task @taskId status is FINISHED
      And the json object "task.result.newCommit.id" equals "{@ObjectId|repo1|@txId|master}"
      And the json response "task.result.newCommit" should contain "tree"
      And the json object "task.result.newCommit.message" equals "Merge: Imported Geopackage"
      And the json object "task.result.importCommit.id" equals "{@ObjectId|repo1|@txId|master^2}"
      And the json response "task.result.importCommit" should contain "tree"
      And the json object "task.result.importCommit.message" equals "Imported Geopackage"
      And the json object "task.result.NewFeatures.type[0].name" equals "Points"
      And the json response "task.result.NewFeatures.type[0].id[0].provided" should contain ""
      And the json response "task.result.NewFeatures.type[0].id[0].assigned" should contain ""
      And I save the json response "task.result.NewFeatures.type[0].id[0].assigned" as "@newFeatureId"
      And I end the transaction with id "@txId" on the "repo1" repo
      And the repo1 repository's "HEAD" should have the following features:
          |    Points     |   Lines    |    Polygons     |
          |    Point.2    |   Line.1   |    Polygon.1    |
          |    Point.3    |   Line.2   |    Polygon.2    |
          |{@newFeatureId}|   Line.3   |    Polygon.3    |
      And I prune the task @taskId

  Scenario: Import an interchange geopackage with conflicting merge, JSON requested response
    Given There is a default multirepo server
      And I export Points from "repo1" to a geopackage file with audit logs as @gpkgFile
     When I modify the Point features in the geopackage file @gpkgFile
      And I add Points/4 to the geopackage file @gpkgFile
      And I have removed "Point.1" on the "repo1" repo in the "" transaction
      And I have a transaction as "@txId" on the "repo1" repo
      And I post @gpkgFile as "fileUpload" to "/repos/repo1/import.json?format=gpkg&message=Imported%20Geopackage&interchange=true&transactionId={@txId}"
     Then the response status should be '200'
      And the response is a JSON async task @taskId
      And the JSON task @taskId description contains "Importing GeoPackage database file."
      And when the JSON task @taskId finishes
     Then the JSON task @taskId status is FAILED
      And the json response "task.result.Merge" should contain "ours"
      And the json response "task.result.Merge" should contain "theirs"
      And the json response "task.result.Merge" should contain "ancestor"
      And the json response "task.result.import.importCommit" should contain "id"
      And I save the json response "task.result.import.importCommit.id" as "@importCommit"
      And the json response "task.result.import.importCommit" should contain "tree"
      And the json object "task.result.import.importCommit.message" equals "Imported Geopackage"
      And the json object "task.result.Merge.ours" equals "{@ObjectId|repo1|@txId|master}"
      And the json object "task.result.Merge.theirs" equals "{@importCommit}"
      And the json object "task.result.Merge.ancestor" equals "{@ObjectId|repo1|@txId|master~1}"
      And the json object "task.result.import.NewFeatures.type[0].name" equals "Points"
      And the json response "task.result.import.NewFeatures.type[0].id[0].provided" should contain ""
      And the json response "task.result.import.NewFeatures.type[0].id[0].assigned" should contain ""
      And I save the json response "task.result.import.NewFeatures.type[0].id[0].assigned" as "@newFeatureId"
      And the json object "task.result.Merge.conflicts" equals "1"
      And the repo1 repository's "WORK_HEAD" in the @txId transaction should have the following features:
          |    Points     |   Lines    |    Polygons     | 
          |    Point.2    |   Line.1   |    Polygon.1    | 
          |    Point.3    |   Line.2   |    Polygon.2    | 
          |{@newFeatureId}|   Line.3   |    Polygon.3    | 
      And I prune the task @taskId
