@Commands @ResolveConflict
Feature: ResolveConflict
  The ResolveConflict command allows a user to resolve a conflict with a specific objectId and is supported through the "/repos/{repository}/resolveconflict" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/resolveconflict"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: ResolveConflict outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/resolveconflict?path=somePath/1&objectid=someId"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: ResolveConflict outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/resolveconflict?path=somePath/1&objectid=someId"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: ResolveConflict without a path issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/resolveconflict?transactionId={@txId}&objectid=someId"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'path' was not provided."
      
  @Status500
  Scenario: ResolveConflict without an object ID issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/resolveconflict?transactionId={@txId}&path=Points/Point.1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'objectid' was not provided."
      
  @Status400
  Scenario: ResolveConflict with an invalid path issues a 400 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/resolveconflict?transactionId={@txId}&path=Points&objectid={@ObjectId|repo1|@txId|master:Points/Point.1}"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "empty child path: '/'"
      
  Scenario: ResolveConflict with valid parameters resolves the conflict
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/resolveconflict?transactionId={@txId}&path=Points/Point.1&objectid={@ObjectId|repo1|@txId|master~1:Points/Point.1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Add/text()" equals "Success"
      And There should be no conflicts on the "repo1" repo in the @txId transaction
      And the variable "{@ObjectId|repo1|@txId|STAGE_HEAD:Points/Point.1}" equals "{@ObjectId|repo1|@txId|master~1:Points/Point.1}"