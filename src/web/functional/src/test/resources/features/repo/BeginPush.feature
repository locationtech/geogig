@Repo
@BeginPush
Feature: BeginPush
  "/repos/{repository}/repo/beginpush" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/repo/beginpush"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
