@Commands @Fetch
Feature: Fetch
  The fetch command allows a user to fetch the changes from a remote repo and is supported through the "/repos/{repository}/featurediff" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/fetch"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Fetch outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/fetch"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Fetching without specifying a remote issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/fetch"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Nothing specified to fetch from."
      
  Scenario: Fetching with a remote name specified should fetch from that remote
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/fetch?remote=repo1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 1 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 2 times
      And the response body should contain "branch1"
      And the response body should contain "master"
      
  @HttpTest
  Scenario: Fetching with an http remote name specified should fetch from that remote
    Given There is a default multirepo server with http remotes
     When I call "GET /repos/repo3/fetch?remote=repo1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 1 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 2 times
      And the response body should contain "branch1"
      And the response body should contain "master"
      
  Scenario: Fetching with all should fetch from all remotes
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/fetch?all=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 2 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 5 times
      And the response body should contain "branch1"
      And the response body should contain "branch2"
      And the response body should contain "master"
      And the response body should contain "ADDED_REF"
      And the response body should not contain "REMOVED_REF"
      And the response body should not contain "UPDATED_REF"
      
  @HttpTest
  Scenario: Fetching with all should fetch from all http remotes
    Given There is a default multirepo server with http remotes
     When I call "GET /repos/repo3/fetch?all=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 2 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 5 times
      And the response body should contain "branch1"
      And the response body should contain "branch2"
      And the response body should contain "master"
      And the response body should contain "ADDED_REF"
      And the response body should not contain "REMOVED_REF"
      And the response body should not contain "UPDATED_REF"
      
   Scenario: Fetching with prune should prune remote branches that were deleted
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo2/fetch?all=true&prune=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 1 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 1 times
      And the xpath "/response/Fetch/Remote/Branch/changeType/text()" equals "REMOVED_REF"
      And the xpath "/response/Fetch/Remote/Branch/name/text()" equals "branch2"
      And the xpath "/response/Fetch/Remote/Branch/oldValue/text()" equals "{@ObjectId|repo2|branch2}"
      
   @HttpTest
   Scenario: Fetching with prune should prune remote branches that were deleted from http remote
    Given There is a default multirepo server with http remotes
     When I call "GET /repos/repo2/fetch?all=true&prune=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Fetch/Remote" 1 times
      And the xml response should contain "/response/Fetch/Remote/Branch" 1 times
      And the xpath "/response/Fetch/Remote/Branch/changeType/text()" equals "REMOVED_REF"
      And the xpath "/response/Fetch/Remote/Branch/name/text()" equals "branch2"
      And the xpath "/response/Fetch/Remote/Branch/oldValue/text()" equals "{@ObjectId|repo2|branch2}"