@Repo
@EndPush
Feature: ApplyChanges
  "/repos/{repository}/repo/endpush" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/endpush"
     Then the response status should be '405'
      And the response allowed methods should be "GET"

  @Status500
  Scenario: Verify ending a push without a begin issues a 500
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/endpush"
     Then the response status should be '500'
      And the response ContentType should be "text/plain"
      And the response body should contain "Tried to end a connection that didn't exist."
