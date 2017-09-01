Feature: GeoGig Repository initialization tests specific to stand-alone server

  @Status409
  @CreateRepository
  Scenario: Verify trying to create a repo issues 409 "Conflict" when a repo with the same name already exists
    Given There is a default multirepo server
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
      }
      """
    Then the response status should be '409'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json object "response.error" equals "Cannot run init on an already initialized repository."

  @CreateRepository @Status409
  Scenario: Verify trying to create a repo issues 409 "Conflict" when a repo with the same name already exists, with parentDirectory
    Given There is a default multirepo server
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "parentDirectory": "{@systemTempPath}"
      }
      """
    Then the response status should be '409'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json object "response.error" equals "Cannot run init on an already initialized repository."
