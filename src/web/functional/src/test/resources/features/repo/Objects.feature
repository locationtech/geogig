@Repo
@Objects
Feature: Objects
  "/repos/{repository}/repo/objects{id}" endpoint for fetching a binary representation of a given Object.
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/objects"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
