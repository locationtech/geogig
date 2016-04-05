@RepositoryManagement
Feature: Create Repository
  Creating a repository on the server is done through the "/repos/{repository}/init" command
  The command must be executed using the HTTP PUT method
  If a repository with the provided name already exists, then a 409 "Conflict" error code shall be returned
  If the command succeeds, the response status code is 201 "Created"

  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty multirepo server
    When I call "GET /repos/repo1/init"
    Then the response status should be '405'
    And the response allowed methods should be "PUT"

  Scenario: Verify trying to create an existing repo issues 409 "Conflict"
    Given There is a default multirepo server
    When I call "PUT /repos/repo1/init"
    Then the response status should be '409'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" equals "Cannot run init on an already initialized repository."

  Scenario: Create repository on empty server
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init"
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the response xml matches
      """
      <response>
        <success>true</success>
        <repo>
          <name>repo1</name>
          <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="/repos/repo1.xml" type="application/xml"/>
        </repo>
      </response>
      """
