@Commands @UpdateRef
Feature: UpdateRef
  The UpdateRef command allows a user to manually change the value of a ref and is supported through the "/repos/{repository}/updateref" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/updateref"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: UpdateRef outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/updateref?name=master"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Calling UpdateRef without a ref name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/updateref"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Required parameter 'name' was not provided."
      
  @Status500
  Scenario: Calling UpdateRef without a new value or delete specified issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/updateref?name=master"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Nothing specified to update with, must specify either deletion or new value to update to."

  @Status500
  Scenario: Calling UpdateRef without a nonexistent name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/updateref?name=nonexistent&delete=true"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "Invalid name: nonexistent"
      
  Scenario: Calling UpdateRef with a new value updates the ref
    Given There is a default multirepo server
     When I call "GET /repos/repo1/updateref?name=master&newValue={@ObjectId|repo1|branch1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/ChangedRef/name/text()" equals "refs/heads/master"
      And the xpath "/response/ChangedRef/objectId/text()" equals "{@ObjectId|repo1|branch1}"
      And the variable "{@ObjectId|repo1|master}" equals "{@ObjectId|repo1|branch1}"
      
  Scenario: Calling UpdateRef with the delete parameter deletes the ref
    Given There is a default multirepo server
     When I call "GET /repos/repo1/updateref?name=branch1&delete=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/ChangedRef/name/text()" equals "refs/heads/branch1"
      And the xpath "/response/ChangedRef/objectId/text()" equals "{@ObjectId|repo1|master^1^2}"
      And the variable "{@ObjectId|repo1|branch1}" equals ""