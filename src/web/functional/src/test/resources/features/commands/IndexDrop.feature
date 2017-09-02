@Commands @IndexDrop
Feature: IndexDrop
  The Index Drop command allows a user to remove an existing index from the repository
  The command must be executed using the HTTP DELETE method

  @Status404
  Scenario: Index drop fails with non-existent repository
    Given There is an empty multirepo server
     When I call "DELETE /repos/noRepo/index/drop?treeRefSpec=Points"
      And the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status405
  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
     When I call "GET /repos/repo1/index/drop?treeRefSpec=Points"
     Then the response status should be '405'
      And the response allowed methods should be "DELETE"

  Scenario: Verify dropping spatial index
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "DELETE /repos/repo1/index/drop?treeRefSpec=Points"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/dropped/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" should not have an index
      
  @Status500
  Scenario: Verify 500 status code when tree ref spec is not provided
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "DELETE /repos/repo1/index/drop"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Required parameter 'treeRefSpec' was not provided."
      And the response status should be '500'
