@Commands @RebuildGraph
Feature: RebuildGraph
  The RebuildGraph command allows a user to rebuild the graph database of a repository and is supported through the "/repos/{repository}/rebuildgraph" endpoint
  The command must be executed using the HTTP GET method
  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/rebuildgraph"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: RebuildGraph outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/rebuildgraph"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: RebuildGraph restores missing entries in the graph database
    Given There is a default multirepo server
      And The graph database on the "repo1" repo has been truncated
     When I call "GET /repos/repo1/rebuildgraph"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/RebuildGraph/updatedGraphElements/text()" equals "4"
      And the xml response should contain "/response/RebuildGraph/UpdatedObject" 4 times
      And the response body should contain "{@ObjectId|repo1|master}"
      And the response body should contain "{@ObjectId|repo1|branch1}"
      And the response body should contain "{@ObjectId|repo1|branch2}"
      And the response body should contain "{@ObjectId|repo1|master~1}"
      
  Scenario: The quiet flag prevents all of the restored commits from being reported
    Given There is a default multirepo server
      And The graph database on the "repo1" repo has been truncated
     When I call "GET /repos/repo1/rebuildgraph?quiet=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/RebuildGraph/updatedGraphElements/text()" equals "4"
      And the xml response should contain "/response/RebuildGraph/UpdatedObject" 0 times
      