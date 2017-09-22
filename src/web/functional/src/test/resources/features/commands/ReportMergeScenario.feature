@Commands @ReportMergeScenario
Feature: ReportMergeScenario
  The ReportMergeScenario command allows a user to see the results of a merge between two branches and is supported through the "/repos/{repository}/reportMergeScenario" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/reportMergeScenario"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: ReportMergeScenario outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=branch1"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Calling ReportMergeScenario with no "our" commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/reportMergeScenario?theirCommit=branch1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Required parameter 'ourCommit' was not provided."
      
  @Status500
  Scenario: Calling ReportMergeScenario with no "their" commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Required parameter 'theirCommit' was not provided."
      
  @Status500
  Scenario: Calling ReportMergeScenario with an invalid "our" commit issues a 500 status code
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=nonexistent&theirCommit=branch1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "'our' commit could not be resolved to a commit object."
      
  @Status500
  Scenario: Calling ReportMergeScenario with an invalid "their" commit issues a 500 status code
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=nonexistent"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "'their' commit could not be resolved to a commit object."
    
  Scenario: ReportMergeScenario with two valid commits returns the details of the merge
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=non_conflicting"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/Feature/change/text()" equals "ADDED"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.2"
      And the xpath "/response/Merge/Feature/geometry/text()" equals "POINT (-10 -10)"
      And the xpath "/response/Merge/Feature/crs/text()" equals "EPSG:4326"
      
  Scenario: ReportMergeScenario with conflicting commits returns the details of the merge
    Given There is a repository with multiple branches named repo1
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master&theirCommit=conflicting"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Merge/Feature/change/text()" equals "CONFLICT"
      And the xpath "/response/Merge/Feature/id/text()" equals "Points/Point.1"
      And the xpath "/response/Merge/Feature/ourvalue/text()" equals "{@ObjectId|repo1|master:Points/Point.1}"
      And the xpath "/response/Merge/Feature/theirvalue/text()" equals "0000000000000000000000000000000000000000"
      And the xpath "/response/Merge/Feature/geometry/text()" equals "POINT (0 0)"
      And the xpath "/response/Merge/Feature/crs/text()" equals "EPSG:4326"
      
  Scenario: ReportMergeScenario supports paging
    Given There is a default multirepo server
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master~2&theirCommit=branch1&elementsPerPage=2&page=0"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Merge/Feature" 2 times
      And the response body should contain "Points/Point.2"
      And the response body should contain "Polygons/Polygon.2"
     When I call "GET /repos/repo1/reportMergeScenario?ourCommit=master~2&theirCommit=branch1&elementsPerPage=2&page=1"
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Merge/Feature" 1 times
      And the response body should contain "Lines/Line.2"