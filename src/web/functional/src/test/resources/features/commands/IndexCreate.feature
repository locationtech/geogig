@Commands @IndexCreate
Feature: IndexCreate
  The Index Create command allows a user to add spatial index to a specified layer
  The command must be executed using the HTTP PUT method

  @Status404
  Scenario: Create index fails when repository does not exist
    Given There is an empty repository named repo1
     When I call "PUT /repos/noRepo/index/create?treeRefSpec=Point"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
     
  @Status405
  Scenario: Verify method not allowed on incorrect request type
    Given There is a repo with some data
     When I call "GET /repos/repo1/index/create?treeRefSpec=Points"
     Then the response status should be '405'
      And the response allowed methods should be "PUT"
      
  @Status400
  Scenario: Create index fails when feature tree does not exist
    Given There is an empty repository named repo1
      And I have a transaction as "@txId" on the "repo1" repo
      And I have staged "Point.1" on the "repo1" repo in the "@txId" transaction
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Point"
     Then the response body should contain "Can't find feature tree"
      And the response status should be '400'

  Scenario: Verify success after adding spatial index
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 

  Scenario: Verify creating index with attribute
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          
  Scenario: Verify creating index with bounds
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&bounds=-60,-45,60,45"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-60,-45,60,45"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 

  Scenario: Verify creating index with extra attribute
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&extraAttributes=ip"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repo1 repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 

  Scenario: Verify creating index with full history
    Given There is a default multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&indexHistory=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repo1 repository's "HEAD:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
          |    Point.3   | 
      And the repo1 repository's "branch1:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.2   | 
      And the repo1 repository's "branch2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          |    Point.3   | 
      And the repo1 repository's "master~2:Points" index should have the following features:
          |     index    | 
          |    Point.1   | 
          
  Scenario: Verify creating index with full history and extra attributes
    Given There is a default multirepo server
     When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&indexHistory=true"
     Then the xpath "/response/success/text()" equals "true"
      And the xpath "/response/index/treeName/text()" equals "Points"
      And the response status should be '201'
      And the repo1 repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
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
          
  @Status500
  Scenario: Verify 500 status code when tree ref spec is not provided
    Given There is a repo with some data
     When I call "PUT /repos/repo1/index/create?extraAttributes=sp"
     Then the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Required parameter 'treeRefSpec' was not provided."
      And the response status should be '500'
