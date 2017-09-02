@Commands @GetCommitGraph
Feature: GetCommitGraph
  The get commit graph command allows a user to retrieve the commit graph of a repo and is supported through the "/repos/{repository}/getCommitGraph" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/getCommitGraph"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Getting the commit graph outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/getCommitGraph?commitId=someId"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Getting the commit graph without specifying a commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/getCommitGraph"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'commitId' was not provided."
      
  Scenario: Getting the commit graph without specifying a depth returns the full graph
    Given There is a default multirepo server
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 5 times
      And the response body should contain "{@ObjectId|repo1|master}"
      And the response body should contain "{@ObjectId|repo1|master~1}"
      And the response body should contain "{@ObjectId|repo1|master~2}"
      And the response body should contain "{@ObjectId|repo1|branch1}"
      And the response body should contain "{@ObjectId|repo1|branch2}"
      
  Scenario: Getting the commit graph with a depth of 0 returns the full graph
    Given There is a default multirepo server
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}&depth=0"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 5 times
      And the response body should contain "{@ObjectId|repo1|master}"
      And the response body should contain "{@ObjectId|repo1|master~1}"
      And the response body should contain "{@ObjectId|repo1|master~2}"
      And the response body should contain "{@ObjectId|repo1|branch1}"
      And the response body should contain "{@ObjectId|repo1|branch2}"
 
  Scenario: Getting the commit graph with a depth of 2 returns only the commit and its parents
    Given There is a default multirepo server
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}&depth=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 3 times
      And the response body should contain "{@ObjectId|repo1|master}"
      And the response body should contain "{@ObjectId|repo1|master~1}"
      And the response body should contain "{@ObjectId|repo1|branch2}"
      
  Scenario: Getting the commit graph should support paging
    Given There is a default multirepo server
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}&show=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 2 times
      And the response body should contain "{@ObjectId|repo1|master}"
      And the response body should contain "{@ObjectId|repo1|master~1}"
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}&show=2&page=1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 2 times
      And the response body should contain "{@ObjectId|repo1|branch2}"
      And the response body should contain "{@ObjectId|repo1|master~2}"
     When I call "GET /repos/repo1/getCommitGraph?commitId={@ObjectId|repo1|master}&show=2&page=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/commit" 1 times
      And the response body should contain "{@ObjectId|repo1|branch1}"
      