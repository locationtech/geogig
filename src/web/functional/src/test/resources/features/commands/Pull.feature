@Commands @Pull @Ignore
Feature: Pull
  The pull command allows a user to merge a remote branch into a local one and is supported through the "/repos/{repository}/pull" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/pull"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Pull outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/pull"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Pull outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/pull"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  Scenario: Pulling from a remote with remote changes updates the local ref
    Given There is a default multirepo server with remotes
      And the variable "{@ObjectId|repo4|master}" equals "{@ObjectId|repo1|master~2}"
      And I have a transaction as "@txId" on the "repo4" repo
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Pull/Merge/ours/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/mergedCommit/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Added/text()" equals "6"
      And the xpath "/response/Pull/Modified/text()" equals "0"
      And the xpath "/response/Pull/Removed/text()" equals "0"
      And the variable "{@ObjectId|repo4|@txId|master}" equals "{@ObjectId|repo1|master}"
      
  @HttpTest
  Scenario: Pulling from an http remote with remote changes updates the local ref
    Given There is a default multirepo server with http remotes
      And the variable "{@ObjectId|repo4|master}" equals "{@ObjectId|repo1|master~2}"
      And I have a transaction as "@txId" on the "repo4" repo
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Pull/Merge/ours/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/mergedCommit/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Added/text()" equals "6"
      And the xpath "/response/Pull/Modified/text()" equals "0"
      And the xpath "/response/Pull/Removed/text()" equals "0"
      And the variable "{@ObjectId|repo4|@txId|master}" equals "{@ObjectId|repo1|master}"
      
  Scenario: Pulling from a remote with both local and remote changes creates a merge commit
    Given There is a default multirepo server with remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.1_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Pull/Merge/ours/text()" equals "{@ObjectId|repo4|@txId|master~1}"
      And the xpath "/response/Pull/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/mergedCommit/text()" equals "{@ObjectId|repo4|@txId|master}"
      And the xpath "/response/Pull/Added/text()" equals "6"
      And the xpath "/response/Pull/Modified/text()" equals "0"
      And the xpath "/response/Pull/Removed/text()" equals "0"
     When I call "GET /repos/repo4/cat?objectid={@ObjectId|repo4|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
      
  @HttpTest
  Scenario: Pulling from an http remote with both local and remote changes creates a merge commit
    Given There is a default multirepo server with http remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.1_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Pull/Merge/ours/text()" equals "{@ObjectId|repo4|@txId|master~1}"
      And the xpath "/response/Pull/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Pull/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Pull/Merge/mergedCommit/text()" equals "{@ObjectId|repo4|@txId|master}"
      And the xpath "/response/Pull/Added/text()" equals "6"
      And the xpath "/response/Pull/Modified/text()" equals "0"
      And the xpath "/response/Pull/Removed/text()" equals "0"
     When I call "GET /repos/repo4/cat?objectid={@ObjectId|repo4|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
      
  Scenario: Pulling from a remote with conflicting changes returns the details of the conflict
    Given There is a default multirepo server with remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.2_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo4|@txId|master}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xml response should contain "/response/Merge/Feature" 6 times
      And the response body should contain "CONFLICT"
      And the response body should contain "<theirvalue>{@ObjectId|repo1|master:Points/Point.2}</theirvalue>"
      And the response body should contain "<ourvalue>{@ObjectId|repo4|@txId|master:Points/Point.2}</ourvalue>"
      
  @HttpTest
  Scenario: Pulling from an http remote with conflicting changes returns the details of the conflict
    Given There is a default multirepo server with http remotes
      And I have a transaction as "@txId" on the "repo4" repo
      And I have committed "Point.2_modified" on the "repo4" repo in the "@txId" transaction
     When I call "GET /repos/repo4/pull?transactionId={@txId}&remoteName=origin&ref=master&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo4|@txId|master}"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xml response should contain "/response/Merge/Feature" 6 times
      And the response body should contain "CONFLICT"
      And the response body should contain "<theirvalue>{@ObjectId|repo1|master:Points/Point.2}</theirvalue>"
      And the response body should contain "<ourvalue>{@ObjectId|repo4|@txId|master:Points/Point.2}</ourvalue>"