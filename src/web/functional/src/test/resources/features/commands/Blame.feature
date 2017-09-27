@Commands @Blame
Feature: Blame
  The blame command allows a user to see who last modified each attribute of a feature and is supported through the "/repos/{repository}/blame" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/blame"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Calling blame without a feature path issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/blame"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'path' was not provided."
      
  @Status500
  Scenario: Calling blame with an invalid commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/blame?commit=nonexistent&path=somePath"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Could not resolve branch or commit"
      
  @Status500
  Scenario: Calling blame with an invalid feature path issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/blame?path=nonexistent"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "The supplied path does not exist"
      
  @Status500
  Scenario: Calling blame with a tree path issues a 500 status code
    Given There is a default multirepo server
     When I call "GET /repos/repo1/blame?path=Points"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "The supplied path does not resolve to a feature"
      
  Scenario: Calling blame with a feature path shows who modified each attribute
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
     When I call "GET /repos/repo1/blame?path=Points/Point.1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Blame/Attribute" 3 times
      And there is an xpath "/response/Blame/Attribute/commit/author/name/text()" that equals "Author1"
      And there is an xpath "/response/Blame/Attribute/commit/message/text()" that equals "Added Point.1"
      And there is an xpath "/response/Blame/Attribute/commit/author/name/text()" that equals "Author2"
      And there is an xpath "/response/Blame/Attribute/commit/message/text()" that equals "Modified Point.1"
      
  Scenario: Calling blame with a feature path and commit shows who modified each attribute
    Given There is an empty repository named repo1
      And There is a feature with multiple authors on the "repo1" repo
     When I call "GET /repos/repo1/blame?path=Points/Point.1&commit=HEAD~1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Blame/Attribute" 3 times
      And the xpath "/response/Blame/Attribute/commit/author/name/text()" equals "Author1"
      And the xpath "/response/Blame/Attribute/commit/message/text()" equals "Added Point.1"
      And the response body should not contain "Author2" 
      And the response body should not contain "Modified Point.1"
