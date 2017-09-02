@Commands @LsTree
Feature: LsTree
  The LsTree command allows a user to view the contents of a tree in the repository and is supported through the "/repos/{repository}/ls-tree" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/ls-tree"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: LsTree outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/ls-tree"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: By default, LsTree lists the children of the root tree
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 3 times
      And the response body should contain "Points"
      And the response body should contain "Lines"
      And the response body should contain "Polygons"
      
  Scenario: Supplying the onlyTree parameter to LsTree lists only trees
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree?onlyTree=true&recursive=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 3 times
      And the response body should contain "Points"
      And the response body should contain "Lines"
      And the response body should contain "Polygons"
      
  Scenario: Supplying the recursive parameter to LsTree lists all features
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree?recursive=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 9 times
      And the response body should contain "Point.1"
      And the response body should contain "Point.2"
      And the response body should contain "Point.3"
      And the response body should contain "Line.1"
      And the response body should contain "Line.2"
      And the response body should contain "Line.3"
      And the response body should contain "Polygon.1"
      And the response body should contain "Polygon.2"
      And the response body should contain "Polygon.3"
      
  Scenario: Supplying both the recursive and showTree parameters lists all trees and features
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree?recursive=true&showTree=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 12 times
      And the response body should contain "Points"
      And the response body should contain "Lines"
      And the response body should contain "Polygons"
      And the response body should contain "Point.1"
      And the response body should contain "Point.2"
      And the response body should contain "Point.3"
      And the response body should contain "Line.1"
      And the response body should contain "Line.2"
      And the response body should contain "Line.3"
      And the response body should contain "Polygon.1"
      And the response body should contain "Polygon.2"
      And the response body should contain "Polygon.3"
      
  Scenario: Providing a refspec lists the features of the tree it resolves to
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree?recursive=true&path=branch1:Points"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 2 times
      And the response body should contain "Point.1"
      And the response body should contain "Point.2"
      
  Scenario: Supplying the verbose parameter lists more information about each node
    Given There is a default multirepo server
     When I call "GET /repos/repo1/ls-tree?verbose=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/node" 3 times
      And the response body should contain "Points"
      And the response body should contain "{@PointsTypeID}"
      And the response body should contain "{@ObjectId|repo1|master:Points}"
      And the response body should contain "Lines"
      And the response body should contain "{@LinesTypeID}"
      And the response body should contain "{@ObjectId|repo1|master:Lines}"
      And the response body should contain "Polygons"
      And the response body should contain "{@PolysTypeID}"
      And the response body should contain "{@ObjectId|repo1|master:Polygons}"
      