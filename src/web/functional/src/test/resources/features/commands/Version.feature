@Commands @Version
Feature: Version
  The Version command allows a user to see the geogig version and is supported through the "/repos/{repository}/version" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/version"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Version outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/version"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Version returns geogig version details
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/version"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/ProjectVersion" 1 times
      And the xml response should contain "/response/BuildTime" 1 times
      And the xml response should contain "/response/BuildUserName" 1 times
      And the xml response should contain "/response/BuildUserEmail" 1 times
      And the xml response should contain "/response/GitBranch" 1 times
      And the xml response should contain "/response/GitCommitID" 1 times
      And the xml response should contain "/response/GitCommitTime" 1 times
      And the xml response should contain "/response/GitCommitAuthorName" 1 times
      And the xml response should contain "/response/GitCommitAuthorEmail" 1 times
      And the xml response should contain "/response/GitCommitMessage" 1 times