@Repo @ObjectExists
Feature: ObjectExists
  The ObjectExists resource is used to determine if an objectId exists in the repository and is supported through the "/repos/{repository}/repo/exists" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/repo/exists"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
  @Status404
  Scenario: ObjectExists outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/repo/exists"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
  @Status400
  Scenario: ObjectExists with no object id issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/exists"
     Then the response status should be '400'
      And the response body should contain "You must specify an object id."
  @Status400
  Scenario: ObjectExists with an invalid object id issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/exists?oid=invalid"
     Then the response status should be '400'
      And the response body should contain "You must specify a valid object id."
    
  Scenario: ObjectExists with a nonexistent object id returns "0"
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/exists?oid=0123456789012345678901234567890123456789"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "0"
      
  Scenario: ObjectExists with an existing object id returns "1"
    Given There is a default multirepo server
     When I call "GET /repos/repo1/repo/exists?oid={@ObjectId|repo1|master:Points/Point.1}"
     Then the response status should be '200'
      And the response ContentType should be "text/plain"
      And the response body should contain "1"