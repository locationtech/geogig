@Repo @Parents
Feature: Parents
  The Parents resource returns the parents of a specific commit and is supported through the "/repos/{repository}/repo/getparents" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/getparents"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
  @Status404
  Scenario: Parents outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/repo/getparents"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
  @Status400
  Scenario: Parents with an invalid commit issues a 400 status code
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getparents?commitId=invalid"
     Then the response status should be '400'
      And the response ContentType should be "text/plain"
      And the response body should contain "You must specify a valid commit id."
      
  Scenario: Parents with no commit returns no parents
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getparents"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain ""
    
  Scenario: Parents with a commit returns the parents of that commit
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getparents?commitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "{@ObjectId|repo1|master~1}"
      And the response body should contain "{@ObjectId|repo1|branch2}"