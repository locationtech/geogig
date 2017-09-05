@Repo
@BatchObjects
Feature: BatchObjects
  "/repos/{repository}/repo/batchobjects" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/batchobjects"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
