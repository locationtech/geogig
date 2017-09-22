@Repo
@EndPush
Feature: EndPush
  "/repos/{repository}/repo/endpush" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/endpush"
     Then the response status should be '405'
      And the response allowed methods should be "GET"

  @Status400
  Scenario: Verify ending a push without a begin issues a 400
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/endpush"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Tried to end a connection that didn't exist."
