@Commands @Remove
Feature: Remove
  The remove command allows a user to remove features from the repository and is supported through the "/repos/{repository}/remove" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/remove"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Removing outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/remove?path=somePath"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: Removing outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/remove?path=somePath"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Removing with no path issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/remove?transactionId={@txId}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Required parameter 'path' was not provided."
          
  Scenario: Removing with a feature path removes the specified feature
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | Points  | Lines  | Polygons  |
          | Point.1 | Line.1 | Polygon.1 |
          | Point.2 | Line.2 | Polygon.2 |
          | Point.3 | Line.3 | Polygon.3 |
     When I call "GET /repos/repo1/remove?path=Points/Point.1&transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Deleted/text()" equals "Points/Point.1"
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | Points  | Lines  | Polygons  |
          | Point.2 | Line.1 | Polygon.1 |
          | Point.3 | Line.2 | Polygon.2 |
          |         | Line.3 | Polygon.3 |
          
  @Status400
  Scenario: Removing with a tree path and no recursive parameter issues a 400 status code
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/remove?path=Points&transactionId={@txId}"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "Cannot remove tree Points if recursive or truncate is not specified"
          
  Scenario: Removing with a tree path and the recursive parameter removes the specified feature tree
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | Points  | Lines  | Polygons  |
          | Point.1 | Line.1 | Polygon.1 |
          | Point.2 | Line.2 | Polygon.2 |
          | Point.3 | Line.3 | Polygon.3 |
     When I call "GET /repos/repo1/remove?path=Points&recursive=true&transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Deleted/text()" equals "Points"
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | Lines  | Polygons  |
          | Line.1 | Polygon.1 |
          | Line.2 | Polygon.2 |
          | Line.3 | Polygon.3 |
          
