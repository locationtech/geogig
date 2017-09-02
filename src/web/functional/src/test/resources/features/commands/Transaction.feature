@Commands @Transaction
Feature: Transaction
  Transactions allow a user to perform work without affecting the main repository is supported through the "/repos/{repository}/beginTransaction" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method on begin transaction issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/beginTransaction"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Beginning a transaction outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status405
  Scenario: Verify wrong HTTP method on end transaction issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/endTransaction"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Ending a transaction outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/endTransaction"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Ending a transaction outside of a transaction issues 500 "Transaction required"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/endTransaction"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No transaction was specified"
      
  Scenario: Work on a transaction does not affect the main repository
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId"
     When I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     Then the variable "{@ObjectId|repo1|master:Points/Point.2}" equals ""
      And the variable "{@ObjectId|repo1|@txId|master~1}" equals "{@ObjectId|repo1|master}"
      
  Scenario: Ending a transaction moves the work to the main repository
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId"
     When I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     Then the variable "{@ObjectId|repo1|master:Points/Point.2}" equals ""
      And the variable "{@ObjectId|repo1|@txId|master~1}" equals "{@ObjectId|repo1|master}"
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Transaction/ID/text()" equals "{@txId}"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|master:Points/Point.2}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/feature/id/text()" equals "{@ObjectId|repo1|master:Points/Point.2}"
      And the xml response should contain "/response/feature/attribute" 3 times
      And the response body should contain "StringProp1_2"
      And the response body should contain "2000"
      And the response body should contain "POINT (-10 -10)"
      
  Scenario: Canceling a transaction leaves the main repository without changes
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId"
     When I have committed "Point.2" on the "repo1" repo in the "@txId" transaction
     Then the variable "{@ObjectId|repo1|master:Points/Point.2}" equals ""
      And the variable "{@ObjectId|repo1|@txId|master~1}" equals "{@ObjectId|repo1|master}"
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId}&cancel=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Transaction/ID/text()" equals "{@txId}"
      And the variable "{@ObjectId|repo1|master:Points/Point.2}" equals ""
      
  Scenario: Ending a transaction with conflicting changes returns details of the conflict
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId"
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId2"
     When I have removed "Point.1_modified" on the "repo1" repo in the "@txId" transaction
      And I have committed "Point.1" on the "repo1" repo in the "@txId2" transaction
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId2}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Transaction/ID/text()" equals "{@txId2}"
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~1}"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xpath "/response/Merge/Feature/change/text()" equals "CONFLICT"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.1"
      And the xpath "/response/Merge/Feature/ourvalue/text()" equals "0000000000000000000000000000000000000000"
      And the xpath "/response/Merge/Feature/theirvalue/text()" equals "{@ObjectId|repo1|master:Points/Point.1}"
      
  Scenario: Fixing transaction conflicts and ending again is successful
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId"
     When I call "GET /repos/repo1/beginTransaction"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Transaction/ID"
      And I save the response "/response/Transaction/ID/text()" as "@txId2"
      And I have removed "Point.1_modified" on the "repo1" repo in the "@txId" transaction
      And I have committed "Point.1" on the "repo1" repo in the "@txId2" transaction
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId2}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Transaction/ID/text()" equals "{@txId2}"
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/theirs/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Merge/ours/text()" equals "{@ObjectId|repo1|@txId|master}"
      And the xpath "/response/Merge/ancestor/text()" equals "{@ObjectId|repo1|master~1}"
      And the xpath "/response/Merge/conflicts/text()" equals "1"
      And the xpath "/response/Merge/Feature/change/text()" equals "CONFLICT"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.1"
      And the xpath "/response/Merge/Feature/ourvalue/text()" equals "0000000000000000000000000000000000000000"
      And the xpath "/response/Merge/Feature/theirvalue/text()" equals "{@ObjectId|repo1|master:Points/Point.1}"
     Then I have committed "Point.1" on the "repo1" repo in the "@txId" transaction
     When I call "GET /repos/repo1/endTransaction?transactionId={@txId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Transaction/ID/text()" equals "{@txId}"
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|master:Points/Point.1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/feature/id/text()" equals "{@ObjectId|repo1|master:Points/Point.1}"
      And the xml response should contain "/response/feature/attribute" 3 times
      And the response body should contain "StringProp1_1"
      And the response body should contain "1000"
      And the response body should contain "POINT (0 0)"
     