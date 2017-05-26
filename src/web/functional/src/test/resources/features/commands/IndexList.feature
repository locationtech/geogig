@Commands @IndexList
Feature: IndexList
  The Index List command allows a user to display spatial index info for a feature tree
  The command must be executed using the HTTP GET method

  Scenario: Index list fails non-existent repository
    Given There is a repo with some data
     When I call "GET /repos/noRepo/index/list?treeName=does_not_exist"
     Then the response body should contain "Repository not found."
      And the response status should be '404'

  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     When I call "POST /repos/repo1/index/list"
     Then the response status should be '405'
      And the response allowed methods should be "GET"

  Scenario: Verify index list return for all feature trees
    Given There is a default multirepo server
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Lines"
     When I call "GET /repos/repo1/index/list"
     Then the xpath "/response/success/text()" equals "true"
      And there is an xpath "/response/index/treeName/text()" that equals "Points"
      And there is an xpath "/response/index/treeName/text()" that equals "Lines"
      And the response status should be '200'

  Scenario: Verify correct index list return for a feature tree
    Given There is a repo with some data
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Lines"
     When I call "GET /repos/repo1/index/list?treeName=Points"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response body should not contain "Lines"
      And the response status should be '200'

  Scenario: Verify failed index list return for non-existent feature tree
    Given There is a repo with some data
     When I call "GET /repos/repo1/index/list?treeName=does_not_exist"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "The provided tree name was not found in the HEAD commit."
      And the response status should be '404'
