@Commands
Feature: IndexRebuild
  The Index Rebuild command allows a user to rebuild the spatial index for a specified layer
  The command must be executed using the HTTP POST method

  Scenario: Index Rebuild fails for non-existent repository
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    Then We change, add and commit some more data
    When I call "POST /repos/noRepo/index/rebuild?treeRefSpec=Points"

  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    Then We change, add and commit some more data
    When I call "GET /repos/repo1/index/rebuild?treeRefSpec=Points"
    Then the response status should be '405'
    And the response allowed methods should be "POST"

  Scenario: Verify quad tree after rebuilding spatial index
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    Then We change, add and commit some more data
    When I call "POST /repos/repo1/index/rebuild?treeRefSpec=Points"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/treesRebuilt/text()" equals "2"
    Then the response status should be '201'

