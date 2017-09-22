@Commands @IndexList
Feature: IndexList
  The Index List command allows a user to display spatial index info for a feature tree
  The command must be executed using the HTTP GET method

  @Status404
  Scenario: Index list fails non-existent repository
    Given There is a repo with some data
     When I call "GET /repos/noRepo/index/list?treeName=does_not_exist"
      And the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status405
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

  Scenario: Verify index list return for all feature trees, JSON output_format
    Given There is a default multirepo server
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Lines"
     When I call "GET /repos/repo1/index/list?output_format=json"
     Then the response status should be '200'
      And the response ContentType should be "application/json"
      And the json object "response.success" equals "true"
      And the json response "response.index" should contain "treeName" 2 times

  Scenario: Verify correct index list return for a feature tree
    Given There is a repo with some data
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
      And I call "PUT /repos/repo1/index/create?treeRefSpec=Lines"
     When I call "GET /repos/repo1/index/list?treeName=Points"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response body should not contain "Lines"
      And the response status should be '200'
      
  @Status404
  Scenario: Verify failed index list return for non-existent feature tree
    Given There is a repo with some data
     When I call "GET /repos/repo1/index/list?treeName=does_not_exist"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "The provided tree name was not found in the HEAD commit."
      And the response status should be '404'
