@Repo
@SendObject
Feature: SendObject
  "/repos/{repository}/repo/sendobject" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/sendobject"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
