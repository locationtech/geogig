@Commands @Merge
Feature: Merge
  The merge command allows a user to merge two branches and is supported through the "/repos/{repository}/merge" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/merge"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Merge outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/merge?commit=branch1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: Merge outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/merge?commit=branch1"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Calling merge with no commit to merge issues a 500 status code
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/merge?transactionId={@txId}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Required parameter 'commit' was not provided."
    
  Scenario: Merging two branches returns the details of the merge
    Given There is a repository with multiple branches named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/merge?transactionId={@txId}&commit=non_conflicting"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|master_original}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|non_conflicting}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|non_conflicting~1}"
      And the xpath "/response/Merge/mergedCommit/text()" equals "{@ObjectId|repo1|@txId|master}"
      
  Scenario: Supplying author information to merge is applied to the merge commit
    Given There is a repository with multiple branches named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/merge?transactionId={@txId}&commit=non_conflicting&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|master_original}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|non_conflicting}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|non_conflicting~1}"
      And the xpath "/response/Merge/mergedCommit/text()" equals "{@ObjectId|repo1|@txId|master}"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
      
  Scenario: Supplying the noCommit parameter prevents the merge commit from being created
    Given There is a repository with multiple branches named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/merge?transactionId={@txId}&commit=non_conflicting&noCommit=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|master_original}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|non_conflicting}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|non_conflicting~1}"
      And the xpath "/response/Merge/mergedCommit/text()" equals "{@ObjectId|repo1|master_original}"
      And the variable "{@ObjectId|repo1|@txId|master}" equals "{@ObjectId|repo1|@txId|master_original}"
      
  Scenario: Merging a conflicting branch returns details of the conflicts
    Given There is a repository with multiple branches named repo1
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/merge?transactionId={@txId}&commit=conflicting"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|master_original}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|conflicting}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|conflicting~1}"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xpath "/response/Merge/Feature/change/text()" equals "CONFLICT"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.1"
      And the xpath "/response/Merge/Feature/ourvalue/text()" equals "{@ObjectId|repo1|master_original:Points/Point.1}"
      And the xpath "/response/Merge/Feature/theirvalue/text()" equals "0000000000000000000000000000000000000000"