@Repo @Depth
Feature: Depth
  The Depth resource returns the depth of the repository from a specific commit and is supported through the "/repos/{repository}/repo/getdepth" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/getdepth"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
  @Status404
  Scenario: Depth outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/repo/getdepth"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
  @Status400
  Scenario: Depth with an invalid commit issues a 400 status code
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getdepth?commitId=invalid"
     Then the response status should be '400'
      And the response ContentType should be "text/plain"
      And the response body should contain "You must specify a valid commit id."
      
  Scenario: Depth with no commit returns no depth for a non-shallow repository
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getdepth"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain ""
      
  @ShallowDepth
  Scenario: Depth with no commit returns the depth of a shallow repository
    Given There is a default multirepo server with a shallow clone
     When I call "GET /repos/shallow/repo/getdepth"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "1"
    
  Scenario: Depth with a commit returns the number of ancestors that commit has
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/getdepth?commitId={@ObjectId|repo1|branch1}"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "1"
     When I call "GET /repos/repo1/repo/getdepth?commitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "2"