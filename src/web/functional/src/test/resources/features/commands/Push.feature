@Commands @Push
Feature: Push
  The push command allows a user to push a local branch to a remote and is supported through the "/repos/{repository}/push" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/push"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Push outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/push"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Pushing changes to a remote results in a success
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo1/push?remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "true"
      And the variable "{@ObjectId|repo1|master}" equals "{@ObjectId|repo4|master}"
     When I call "GET /repos/repo1/push?remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "false"
      
  @HttpTest
  Scenario: Pushing changes to an http remote results in a success
    Given There is a default multirepo server with http remotes
     When I call "GET /repos/repo1/push?remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "true"
      And the variable "{@ObjectId|repo1|master}" equals "{@ObjectId|repo4|master}"
     When I call "GET /repos/repo1/push?remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "false"
      
  @Status500
  Scenario: Pushing changes to a remote with other changes issues a 500 status code
    Given There is a default multirepo server with remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.1_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/push?transactionId={@txId}&remoteName=origin&ref=master"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Push failed: The remote repository has changes that would be lost in the event of a push."

  @Status500
  @HttpTest
  Scenario: Pushing changes to an http remote with other changes issues a 500 status code
    Given There is a default multirepo server with http remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.1_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/push?transactionId={@txId}&remoteName=origin&ref=master"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Push failed: The remote repository has changes that would be lost in the event of a push."

  @HttpTest
  Scenario: Pushing changes to an http remote results in a success
    Given There is a default multirepo server with http remotes
      And I have a transaction as "@txId" on the "repo1" repo
      And I have committed "Point.2_modified" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/push?transactionId={@txId}&remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "true"
      And the variable "{@ObjectId|repo1|@txId|master}" equals "{@ObjectId|repo4|master}"
     When I call "GET /repos/repo1/push?transactionId={@txId}&remoteName=repo4&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Push/text()" equals "Success"
      And the xpath "/response/dataPushed/text()" equals "false"