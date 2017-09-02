@Commands @Add
Feature: Add
  The add command allows a user to stage features in the repository and is supported through the "/repos/{repository}/add" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/add"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Adding outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/add"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: Adding outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/add"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."

  Scenario: Adding with no path filter stages all features
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have unstaged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Point.2" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Line.1" on the "repo1" repo in the "@txId" transaction
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | None |
     When I call "GET /repos/repo1/add?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Add/text()" equals "Success"
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          |    Points    |   Lines    |
          |    Point.1   |   Line.1   |
          |    Point.2   |            |

  Scenario: Adding with a path filter stages specified features
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have unstaged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Point.2" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Line.1" on the "repo1" repo in the "@txId" transaction
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          | None |
     When I call "GET /repos/repo1/add?path=Points/Point.1&transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Add/text()" equals "Success"
      And the repo1 repository's "STAGE_HEAD" in the @txId transaction should have the following features:
          |    Points    |
          |    Point.1   |

  Scenario: Adding on a conflicted path resolves the conflict
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/add?path=Points/Point.1&transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Add/text()" equals "Success"
      And There should be no conflicts on the "repo1" repo in the @txId transaction
