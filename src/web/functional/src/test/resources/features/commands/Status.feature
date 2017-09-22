@Commands @Status
Feature: Status
  The Status command allows a user to see the current state of the repository and is supported through the "/repos/{repository}/status" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/status"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Status outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/status"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Status shows the current branch and all staged and unstaged features
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have staged "Line.1" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Point.2" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/status?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/header/branch/text()" equals "master"
      And the xpath "/response/staged/changeType/text()" equals "ADDED"
      And the xpath "/response/staged/newPath/text()" equals "Lines/Line.1"
      And the xml response should contain "/response/unstaged" 2 times
      And the response body should contain "Points/Point.1"
      And the response body should contain "Points/Point.2"
          
  Scenario: Status shows conflicted features
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/status?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/header/branch/text()" equals "master"
      And the xpath "/response/unmerged/changeType/text()" equals "CONFLICT"
      And the xpath "/response/unmerged/path/text()" equals "Points/Point.1"
      And the xpath "/response/unmerged/ours/text()" equals "{@ObjectId|repo1|@txId|master:Points/Point.1}"
      And the xpath "/response/unmerged/theirs/text()" equals "0000000000000000000000000000000000000000"
      And the xpath "/response/unmerged/ancestor/text()" equals "{@ObjectId|repo1|@txId|master~1:Points/Point.1}"
      