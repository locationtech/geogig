@Commands @FeatureDiff
Feature: FeatureDiff
  The feature diff command allows a user to see the difference between two versions of a specific feature and is supported through the "/repos/{repository}/featurediff" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/featurediff"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Feature diff outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/featurediff?path=somePath"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Calling feature diff without specifying a path issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/featurediff"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'path' was not provided."
      
  @Status500
  Scenario: Calling feature diff with an empty path issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/featurediff?path=%20"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Invalid path was specified"
      
  Scenario: Feature diff should work for an added feature
    Given There is a default multirepo server
     When I call "GET /repos/repo1/featurediff?path=Points/Point.3&oldTreeish=master~1&newTreeish=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 3 times
      And the response body should contain "ADDED"
      And the response body should contain "StringProp1_3"
      And the response body should contain "3000"
      And the response body should contain "POINT (10 10)"
      And the response body should not contain "MODIFIED"
      And the response body should not contain "REMOVED"
  
  Scenario: Feature diff should work for a removed feature
    Given There is a default multirepo server
     When I call "GET /repos/repo1/featurediff?path=Points/Point.3&oldTreeish=master&newTreeish=master~1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 3 times
      And the response body should contain "REMOVED"
      And the response body should contain "StringProp1_3"
      And the response body should contain "3000"
      And the response body should contain "POINT (10 10)"
      And the response body should not contain "MODIFIED"
      And the response body should not contain "ADDED"
  
  Scenario: Feature diff should work for a modified feature
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
     When I call "GET /repos/repo1/featurediff?path=Points/Point.1&oldTreeish=master~1&newTreeish=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 1 times
      And the xpath "/response/diff/attributename/text()" equals "ip"
      And the xpath "/response/diff/changetype/text()" equals "MODIFIED"
      And the xpath "/response/diff/oldvalue/text()" equals "1000"
      And the xpath "/response/diff/newvalue/text()" equals "1500"
      And the response body should not contain "ADDED"
      And the response body should not contain "REMOVED"