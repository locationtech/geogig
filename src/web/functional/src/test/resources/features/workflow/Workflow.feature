@Workflow
Feature: Verify workflow

    Scenario: Simulate a user's workflow
        Given There is an empty multirepo server
        Given There is an empty repository named repo1
        Given There are multiple branches on the "repo1" repo
        And I have a geopackage file @gpkgFile
        And I have a transaction as "@txId" on the "repo1" repo
        When I post @gpkgFile as "fileUpload" to "/repos/repo1/import?format=gpkg&transactionId={@txId}"
        Then the response status should be '200'
        And the response is an XML async task @taskId
        And the task @taskId description contains "Importing GeoPackage database file."
        And when the task @taskId finishes
        Then the task @taskId status is FINISHED
        And the xml response should contain "/task/result/commit/id"
        And the xml response should contain "/task/result/commit/tree"
        And I end the transaction with id "@txId" on the "repo1" repo
        And the repo1 repository's "HEAD" should have the following features:
              |    Points    |   Lines    |    Polygons     |
              |    Point.1   |   Line.1   |    Polygon.1    |
              |    Point.2   |   Line.2   |    Polygon.2    |
              |    Point.3   |   Line.3   |    Polygon.3    |
        And I prune the task @taskId
        Given There are three repos with remotes 
        And I have a transaction as "@txId2" on the "repo2" repo
        When I call "GET /repos/repo2/add?path=Points/Point.1&transactionId={@txId2}"
        Then the response status should be '200'
        And the xpath "/response/success/text()" equals "true"
        And the xpath "/response/Add/text()" equals "Success"
        And There should be no conflicts on the "repo2" repo in the @txId2 transaction
        And I have staged "Point.1" on the "repo2" repo in the "@txId2" transaction
        When I call "GET /repos/repo2/commit?transactionId={@txId2}&message=My%20Message"
        Then the response status should be '200'
        And the xpath "/response/success/text()" equals "true"
        And the xpath "/response/commitId/text()" equals "{@ObjectId|repo2|@txId2|master}"
        And the xpath "/response/added/text()" equals "1"
        And the xpath "/response/changed/text()" equals "0"
        And the xpath "/response/deleted/text()" equals "0"
        And I end the transaction with id "@txId2" on the "repo2" repo
        When I call "GET /repos/repo3/remote?remoteName=nonexistent&ping=true"
        Then the response status should be '200'
        And the xpath "/response/success/text()" equals "true"
        And the xpath "/response/ping/success/text()" equals "false"
        When I call "GET /repos/repo3/remote?list=true"
        Then the response status should be '200'
        When I call "GET /repos/repo3/pull?remoteName=repo2&ref=master"
        Then the response status should be '200'
        Given I have a transaction as "@txId3" on the "repo2" repo
        When I call "GET /repos/repo2/add?path=Lines/Line.1&transactionId={@txId3}"
        Then the response status should be '200'
        And I have staged "Line.1" on the "repo2" repo in the "@txId3" transaction
        When I call "GET /repos/repo2/commit?transactionId={@txId3}&message=New%20Message"
        Then the response status should be '200'
        When I call "GET /repos/repo4/pull?remoteName=repo2&ref=master"
        Then the response status should be '200'
        And I end the transaction with id "@txId3" on the "repo2" repo
        Given I have a transaction as "@txId4" on the "repo4" repo
        When I call "GET /repos/repo4/add?path=Lines/line.2&transactionId={@txId4}"
        Then the response status should be '200'
        And I have staged "Line.2" on the "repo4" repo in the "@txId4" transaction
        When I call "GET /repos/repo4/commit?transactionId={@txId4}&message=Another%20Message"
        Then the response status should be '200'

