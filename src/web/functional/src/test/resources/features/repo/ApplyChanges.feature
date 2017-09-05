@Repo
@ApplyChanges
Feature: ApplyChanges
  "/repos/{repository}/repo/applychanges" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/applychanges"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
  @Status500
  Scenario: Apply chnages with no post data issues a 500
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/repo/applychanges"
     Then the response status should be '500'
