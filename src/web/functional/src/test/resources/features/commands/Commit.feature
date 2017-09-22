@Commands @Commit
Feature: Commit
  The commit command allows a user to commit staged changes and is supported through the "/repos/{repository}/commit" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/commit"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Commit outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/commit"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: Commit outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/commit"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Calling commit with no changes creates an empty commit
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/commit?transactionId={@txId}&message=My%20Message"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commitId/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/added/text()" equals "0"
      And the xpath "/response/changed/text()" equals "0"
      And the xpath "/response/deleted/text()" equals "0"
      
  Scenario: Calling commit with unstaged features creates an empty commit
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have unstaged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Point.2" on the "repo1" repo in the "@txId" transaction
      And I have unstaged "Line.1" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/commit?transactionId={@txId}&message=My%20Message"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commitId/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/added/text()" equals "0"
      And the xpath "/response/changed/text()" equals "0"
      And the xpath "/response/deleted/text()" equals "0"
       
  Scenario: Calling commit with staged features commits all staged features
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have staged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have staged "Point.2" on the "repo1" repo in the "@txId" transaction
      And I have staged "Line.1" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/commit?transactionId={@txId}&message=My%20Message"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commitId/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/added/text()" equals "3"
      And the xpath "/response/changed/text()" equals "0"
      And the xpath "/response/deleted/text()" equals "0"
      
  Scenario: I should be able supply commit with a different author name and commit message
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have staged "Point.1" on the "repo1" repo in the "@txId" transaction
      And I have staged "Point.2" on the "repo1" repo in the "@txId" transaction
      And I have staged "Line.1" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/commit?transactionId={@txId}&message=My%20Message&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commitId/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/added/text()" equals "3"
      And the xpath "/response/changed/text()" equals "0"
      And the xpath "/response/deleted/text()" equals "0"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
      And the xpath "/response/commit/message/text()" contains "My Message"