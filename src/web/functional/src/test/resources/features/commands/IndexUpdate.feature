@Commands @IndexUpdate
Feature: IndexUpdate
  The Index Update command allows a user to update the spatial index with extra attributes
  The command must be executed using the HTTP POST method

  @Status404
  Scenario: Index update fails with non-existent repository
    Given There is an empty multirepo server
     When I call "POST /repos/noRepo/index/update?treeRefSpec=Points&extraAttributes=ip&add=true"
      And the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status405
  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
     When I call "GET /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&add=true"
     Then the response status should be '405'
      And the response allowed methods should be "POST"

  Scenario: Verify updating spatial index by adding attributes
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=sp"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&add=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'

  Scenario: Verify success when overwriting the attributes of an index
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&overwrite=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      
  Scenario: Verify success when removing the attributes of an index
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&overwrite=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      
  Scenario: Verify success when updating the bounds
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&bounds=-60,-45,60,45"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index bounds should be "-60,-45,60,45"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      
  Scenario: Verify success when updating the whole history of an index
    Given There is a default multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&indexHistory=true"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "master~2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&overwrite=true&indexHistory=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "branch1:Points" index should track the extra attribute "ip"
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "branch2:Points" index should track the extra attribute "ip"
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "master~2:Points" index should track the extra attribute "ip"
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      

  Scenario: Verify success when updating only the head commit of an index
    Given There is a default multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&indexHistory=true"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "master~2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&overwrite=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should track the extra attribute "sp"
      And the repo1 repository's "master~2:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
      And the response status should be '201'
      
  @Status500
  Scenario: Verify 500 status code when tree ref spec is not provided
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
     When I call "POST /repos/repo1/index/update"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Required parameter 'treeRefSpec' was not provided."
      And the response status should be '500'


