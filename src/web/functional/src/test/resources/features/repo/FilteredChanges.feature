@Repo
@FilteredChanges
Feature: FilterChanges
  "/repos/{repository}/repo/filterchanges" endpoint

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/filteredchanges"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
      
  @Status500
  Scenario: No commit specified returns 500
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/filteredchanges" with
      """
      {
      }
      """
     Then the response status should be '500'
      And the response body should contain "Object does not exist: 0000000000000000000000000000000000000000"

  Scenario: Commit specified returns 200
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/filteredchanges" with
      """
      {
          "commitId"="{@ObjectId|repo1|master}"
      }
      """
     Then the response status should be '200'
