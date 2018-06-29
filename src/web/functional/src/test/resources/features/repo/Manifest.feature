@Repo @Manifest
Feature: Manifest
  The manifest resources provides a list of all the branches in the repository and is supported through the "/repos/{repository}/repo/manifest" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/manifest"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
    
  Scenario: Manifest lists all of the branches in the repository
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/manifest"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "HEAD refs/heads/master {@ObjectId|repo1|master}"
      And the response body should contain "refs/heads/master {@ObjectId|repo1|master}"
      And the response body should contain "refs/heads/branch1 {@ObjectId|repo1|branch1}"
      And the response body should contain "refs/heads/branch2 {@ObjectId|repo1|branch2}"
      
  Scenario: Manifest also lists remote branches when the remotes parameter is specified
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo2/repo/manifest?remotes=true"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "HEAD refs/heads/master {@ObjectId|repo2|master}"
      And the response body should contain "refs/heads/master {@ObjectId|repo2|master}"
      And the response body should contain "refs/heads/branch1 {@ObjectId|repo2|branch1}"
      And the response body should contain "refs/heads/branch2 {@ObjectId|repo2|branch2}"
      #And the response body should contain "refs/remotes/origin/HEAD {@ObjectId|repo1|master}"
      And the response body should contain "refs/remotes/origin/master {@ObjectId|repo1|master}"
      And the response body should contain "refs/remotes/origin/branch1 {@ObjectId|repo1|branch1}"
      And the response body should contain "refs/remotes/origin/branch2 {@ObjectId|repo1|branch2}"