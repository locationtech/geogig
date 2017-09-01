@Repo @AffectedFeatures
Feature: AffectedFeatures
  The AffectedFeatures resource provides a list of features changed in a commit and is supported through the "/repos/{repository}/repo/affectedfeatures" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/affectedfeatures"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
  @Status400
  Scenario: AffectedFeatures outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/repo/affectedfeatures"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
  @Status400
  Scenario: AffectedFeatures with no commit issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/affectedfeatures"
     Then the response status should be '400'
      And the response body should contain "You must specify a commit id."
  @Status400
  Scenario: AffectedFeatures with an invalid commit issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/affectedfeatures?commitId=invalid"
     Then the response status should be '400'
      And the response body should contain "You must specify a valid commit id."
    
  Scenario: AffectedFeatures lists all features changed in a commit
    Given There is a default multirepo server
      And I have committed "Point.1_modified" on the "repo1" repo in the "" transaction
     When I call "GET /repos/repo1/repo/affectedfeatures?commitId={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "{@ObjectId|repo1|master~1:Points/Point.1}"