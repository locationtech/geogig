@Commands
Feature: RefParse
  The RefParse command allows a user to get the details of a ref by name and is supported through the "/repos/{repository}/refparse" endpoint
  The command must be executed using the HTTP GET method

  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/refparse"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
    
  Scenario: RefParse outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/refparse"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
      
  Scenario: Calling RefParse without a ref name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/refparse"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No name was given."
      
  Scenario: Calling RefParse without a nonexistent name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/refparse?name=nonexistent"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Unable to parse the provided name."
      
  Scenario: Calling RefParse with a ref name returns the details of that ref
    Given There is a default multirepo server
     When I call "GET /repos/repo1/refparse?name=master"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Ref/name/text()" equals "refs/heads/master"
      And the xpath "/response/Ref/objectId/text()" equals "{@ObjectId|repo1|master}"