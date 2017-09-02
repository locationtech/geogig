@Commands @IndexRebuild
Feature: IndexRebuild
  The Index Rebuild command allows a user to rebuild the spatial index for a specified layer
  The command must be executed using the HTTP POST method

  @Status404
  Scenario: Index Rebuild fails for non-existent repository
    Given There is an empty multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
      And the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status405
  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     When I call "GET /repos/repo1/index/rebuild?treeRefSpec=Points"
     Then the response status should be '405'
      And the response allowed methods should be "POST"

  Scenario: Verify quad tree after rebuilding spatial index
    Given There is a default multirepo server
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
     Then the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" should not have an index
      And the repo1 repository's "branch2:Points" should not have an index
      And the repo1 repository's "master~2:Points" should not have an index
     When I call "POST /repos/repo1/index/rebuild?treeRefSpec=Points"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/treesRebuilt/text()" equals "4"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "master~2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      
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
     When I call "POST /repos/repo1/index/rebuild"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Required parameter 'treeRefSpec' was not provided."
      And the response status should be '500'