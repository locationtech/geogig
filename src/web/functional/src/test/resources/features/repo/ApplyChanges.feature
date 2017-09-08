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
  @Status400
  Scenario: Apply changes with no post data issues a 400
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/repo/applychanges"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
