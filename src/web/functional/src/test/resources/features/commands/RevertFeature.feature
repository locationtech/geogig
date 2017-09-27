@Commands @RevertFeature
Feature: RevertFeature
  The RevertFeature command allows a user to undo the changes made to a feature and is supported through the "/repos/{repository}/revertfeature" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/revertfeature"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: RevertFeature outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/revertfeature?path=somePath/1&newCommitId=someId&oldCommitId=someId"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  @Status404
  Scenario: RevertFeature outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/revertfeature?path=somePath/1&newCommitId=someId&oldCommitId=someId"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: RevertFeature without a path issues a 500 status code
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&oldCommitId={@ObjectId|repo1|master}&newCommitId={@ObjectId|repo1|master}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'path' was not provided."
      
  @Status500
  Scenario: RevertFeature without a new commit ID issues a 500 status code
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|master}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'newCommitId' was not provided."
      
  @Status500
  Scenario: RevertFeature without an old commit ID issues a 500 status code
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&newCommitId={@ObjectId|repo1|master}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'oldCommitId' was not provided."
      
  @Status500
  Scenario: RevertFeature with an invalid path issues a 500 status code
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=nonexistent&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "The feature was not found in either commit tree."
      
  Scenario: RevertFeature on an added feature removes that feature
    Given There is a default multirepo server
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.3&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the repo1 repository's "master" in the @txId transaction should have the following features:
          | Points  | Lines  | Polygons  |
          | Point.1 | Line.1 | Polygon.1 |
          | Point.2 | Line.2 | Polygon.2 |
          |         | Line.3 | Polygon.3 |
          
  Scenario: RevertFeature on a modified feature undoes the change
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the variable "{@ObjectId|repo1|@txId|master:Points/Point.1}" equals "{@ObjectId|repo1|@txId|master~2:Points/Point.1}"
      
  Scenario: RevertFeature on a removed feature adds it back
    Given There is a repository with multiple branches named repo1
      And I have checked out "conflicting" on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
      And the repo1 repository's "conflicting" in the @txId transaction should have the following features:
          | Points  |
          |         |
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|conflicting~1}&newCommitId={@ObjectId|repo1|conflicting}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the repo1 repository's "conflicting" in the @txId transaction should have the following features:
          | Points  |
          | Point.1 |
          
  Scenario: If a feature has changed since the 'new' commit id, conflicts may be thrown
    Given There is a repository with multiple branches named repo1
      And I have checked out "conflicting" on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
      And I have committed "Point.1_modified" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|@txId|conflicting~2}&newCommitId={@ObjectId|repo1|@txId|conflicting~1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xpath "/response/Merge/Feature/change/text()" equals "CONFLICT"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.1"
      And the xpath "/response/Merge/Feature/ourvalue/text()" equals "{@ObjectId|repo1|@txId|conflicting:Points/Point.1}"
      And the xpath "/response/Merge/Feature/theirvalue/text()" equals "{@ObjectId|repo1|@txId|conflicting~2:Points/Point.1}"
      
  Scenario: Supplying author information applies to the reverted commit as well as the merge commit
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
      And I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}&authorName=myAuthor&authorEmail=myAuthor@geogig.org"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master^2}"
     Then the response status should be '200'
      And the xpath "/response/commit/author/name/text()" equals "myAuthor"
      And the xpath "/response/commit/author/email/text()" equals "myAuthor@geogig.org"
      
  Scenario: Supplying a commit message applies to the revert commit
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
      And I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}&commitMessage=My%20Message"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master^2}"
     Then the response status should be '200'
      And the xpath "/response/commit/message/text()" equals "My Message"
      
  Scenario: Supplying a merge message applies to the merge commit
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
      And I have a transaction as "@txId" on the "repo1" repo
      And I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/revertfeature?transactionId={@txId}&path=Points/Point.1&oldCommitId={@ObjectId|repo1|master~1}&newCommitId={@ObjectId|repo1|master}&mergeMessage=My%20Message"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|@txId|master}"
     Then the response status should be '200'
      And the xpath "/response/commit/message/text()" equals "My Message"