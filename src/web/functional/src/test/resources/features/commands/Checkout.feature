@Commands @Checkout
Feature: Checkout
  The checkout command allows a user to switch branches or resolve conflicts and is supported through the "/repos/{repository}/checkout" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/checkout"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Checkout outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/checkout"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: Checkout outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/checkout"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Calling checkout without specifying a branch or path issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/checkout?transactionId={@txId}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No branch or commit specified for checkout."
      
  Scenario: Calling checkout with a branch name changes the current branch
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/checkout?transactionId={@txId}&branch=branch1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/OldTarget/text()" equals "refs/heads/master"
      And the xpath "/response/NewTarget/text()" equals "branch1"
      
  Scenario: Calling checkout with a conflicted path with 'ours' will checkout 'our' version of the feature
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/checkout?transactionId={@txId}&path=Points/Point.1&ours=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Path/text()" equals "Points/Point.1"
      And the xpath "/response/Strategy/text()" equals "ours"
      And the variable "{@ObjectId|repo1|@txId|WORK_HEAD:Points/Point.1}" equals "{@ObjectId|repo1|@txId|master:Points/Point.1}"
      
  Scenario: Calling checkout with a conflicted path with 'theirs' will checkout 'their' version of the feature
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/checkout?transactionId={@txId}&path=Points/Point.1&theirs=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Path/text()" equals "Points/Point.1"
      And the xpath "/response/Strategy/text()" equals "theirs"
      And the variable "{@ObjectId|repo1|@txId|WORK_HEAD:Points/Point.1}" equals "{@ObjectId|repo1|@txId|branch1:Points/Point.1}"
      
   @Status500
   Scenario: Calling checkout with a conflicted path with neither 'ours' or 'theirs' issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And There are conflicts on the "repo1" repo in the @txId transaction
     When I call "GET /repos/repo1/checkout?transactionId={@txId}&path=Points/Point.1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Please specify either ours or theirs to update the feature path specified."