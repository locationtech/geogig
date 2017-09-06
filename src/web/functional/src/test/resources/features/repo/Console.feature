@Repo
@Console
Feature: Console
  "/repos/{repository}/repo/console" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/console"
     Then the response status should be '405'
      And the response allowed methods should be "GET,POST"
