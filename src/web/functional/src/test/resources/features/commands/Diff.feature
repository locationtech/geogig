@Commands
Feature: Diff
  The diff command allows a user to see the difference between two commits and is supported through the "/repos/{repository}/config" endpoint
  The command must be executed using the HTTP GET method

  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/diff"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
    
  Scenario: Diff outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/diff"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
      
  Scenario: Calling diff without specifying an old ref spec issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/diff"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No old ref spec"
      
  Scenario: Calling diff with an empty old ref spec issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/diff?oldRefSpec=%20"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "No old ref spec"
      
  Scenario: Calling diff with an old ref spec returns all of the changes since that commit
    Given There is a default multirepo server
     When I call "GET /repos/repo1/diff?oldRefSpec=master~2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 6 times
      And the response body should contain "Points/Point.2"
      And the response body should contain "Points/Point.3"
      And the response body should contain "Lines/Line.2"
      And the response body should contain "Lines/Line.3"
      And the response body should contain "Polygons/Polygon.2"
      And the response body should contain "Polygons/Polygon.3"
      
  Scenario: Calling diff with two ref specs returns all of the changes since those commits
    Given There is a default multirepo server
     When I call "GET /repos/repo1/diff?oldRefSpec=master~2&newRefSpec=master~1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 3 times
      And the response body should contain "Points/Point.2"
      And the response body should contain "Lines/Line.2"
      And the response body should contain "Polygons/Polygon.2"
      
  Scenario: Diff should support paging results
    Given There is a default multirepo server
     When I call "GET /repos/repo1/diff?oldRefSpec=master~2&page=0&show=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 2 times
      And the response body should contain "Points/Point.2"
      And the response body should contain "Points/Point.3"
     When I call "GET /repos/repo1/diff?oldRefSpec=master~2&page=1&show=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 2 times
      And the response body should contain "Polygons/Polygon.2"
      And the response body should contain "Polygons/Polygon.3"
     When I call "GET /repos/repo1/diff?oldRefSpec=master~2&page=2&show=2"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/diff" 2 times
      And the response body should contain "Lines/Line.2"
      And the response body should contain "Lines/Line.3"