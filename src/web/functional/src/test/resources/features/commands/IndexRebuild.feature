@Commands
Feature: IndexRebuild
  The Index Rebuild command allows a user to rebuild the spatial index for a specified layer
  The command must be executed using the HTTP POST method

  Scenario: Index Rebuild fails for non-existent repository
    Given There is an empty multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     Then the response body should contain "Repository not found."
      And the response status should be '404'

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

