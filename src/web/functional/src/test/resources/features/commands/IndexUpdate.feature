@Commands
Feature: IndexUpdate
  The Index Update command allows a user to update the spatial index with an attribute
  The command must be executed using the HTTP POST method

  Scenario: Index update fails with non-existent repository
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=sp"
    When I call "POST /repos/noRepo/index/update?treeRefSpec=Points&attribute=ip&add=true"
    And the response body should contain "Repository not found."
    Then the response status should be '404'

  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=sp"
    When I call "GET /repos/repo1/index/update?treeRefSpec=Points&attribute=ip&add=true"
    Then the response status should be '405'
    And the response allowed methods should be "POST"

  Scenario: Verify updating spatial index by adding attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=sp"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=ip&add=true"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify updating spatial index by adding attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=sp"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=ip&add=true"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify success after updating spatial index on attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=sp"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify success when over-writing spatial index on attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&overwrite=true"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'



