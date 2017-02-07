@Commands
Feature: IndexCreate
  The Index Create command allows a user to add spatial index to a specified layer
  The command must be executed using the HTTP PUT method

  Scenario: Create index fails when repository does not exist
    Given There is an empty repository named repo1
    When I call "PUT /repos/noRepo/index/create?treeRefSpec=Point"
    Then the response body should contain "Repository not found."
    Then the response status should be '404'
    
  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
    When I call "GET /repos/repo1/index/create?treeRefSpec=Points"
    Then the response status should be '405'
    And the response allowed methods should be "PUT"

  Scenario: Create index fails when feature tree does not exist
    Given There is an empty repository named repo1
    And I have a transaction as "@txId" on the "repo1" repo
    And I have staged "Point.1" on the "repo1" repo in the "@txId" transaction
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Point"
    Then the response body should contain "Can't find feature tree"
    Then the response status should be '400'

  Scenario: Verify success after adding spatial index
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify creating index with attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&attribute=sp"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify creating index with extra attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&extraAttribute=ip"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

  Scenario: Verify creating index with full history
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&indexHistory=true"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/index/treeName/text()" equals "Points"
    Then the response status should be '201'

