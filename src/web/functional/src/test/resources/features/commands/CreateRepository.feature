@RepositoryManagement @CreateRepository
Feature: Create Repository
  Creating a repository on the server is done through the "/repos/{repository}/init" command
  The command must be executed using the HTTP PUT method
  If a repository with the provided name already exists, then a 409 "Conflict" error code shall be returned
  If the command succeeds, the response status code is 201 "Created"

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty multirepo server
    When I call "GET /repos/repo1/init"
    Then the response status should be '405'
    And the response allowed methods should be "PUT"

  @FileRepository @Status409
  Scenario: Verify trying to create an existing repo issues 409 "Conflict"
    Given There is a default multirepo server
    And I have "extraRepo" that is not managed
    When I "PUT" content-type "application/json" to "/repos/extraRepo/init" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '409'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" equals "Cannot run init on an already initialized repository."

  Scenario: Create repository on empty server
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init"
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed", JSON requested response
    Given There is an empty multirepo server
    When I call "GET /repos/repo1/init.json"
    Then the response status should be '405'
    And the response allowed methods should be "PUT"

  @FileRepository @Status409
  Scenario: Verify trying to create an existing repo issues 409 "Conflict", JSON requested response
    Given There is a default multirepo server
    And I have "extraRepo" that is not managed
    When I "PUT" content-type "application/json" to "/repos/extraRepo/init.json" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '409'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json object "response.error" equals "Cannot run init on an already initialized repository."

  Scenario: Create repository on empty server, JSON requested response
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json"
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"

  @FileRepository
  Scenario: Verify JSON fomratted response of Init with JSON formatted request parameters
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json" with the System Temp Directory as the parentDirectory
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"
    And the parent directory of repository "repo1" equals System Temp directory

  @FileRepository
  Scenario: Verify XML fomratted response of Init with JSON formatted request parameters
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init" with the System Temp Directory as the parentDirectory
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"
    And the parent directory of repository "repo1" equals System Temp directory

  @FileRepository
  Scenario: Verify JSON fomratted response of Init with URL Form encoded request parameters
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json" with a URL encoded Form containing a parentDirectory parameter
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"
    And the parent directory of repository "repo1" equals System Temp directory

  @FileRepository
  Scenario: Verify XML fomratted response of Init with URL Form encoded request parameters
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init" with a URL encoded Form containing a parentDirectory parameter
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"
    And the parent directory of repository "repo1" equals System Temp directory

  @FileRepository
  Scenario: Verify JSON fomratted response of Init with JSON formatted request parameters and Author
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json" with Author and the System Temp Directory as the parentDirectory
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"
    And the parent directory of repository "repo1" equals System Temp directory
    And the Author config of repository "repo1" is set

  @FileRepository
  Scenario: Verify XML fomratted response of Init with JSON formatted request parameters and Author
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init" with Author and the System Temp Directory as the parentDirectory
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"
    And the parent directory of repository "repo1" equals System Temp directory
    And the Author config of repository "repo1" is set

  @FileRepository
  Scenario: Verify JSON fomratted response of Init with URL Form encoded request parameters and Author
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json" with a URL encoded Form containing a parentDirectory parameter and Author
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"
    And the parent directory of repository "repo1" equals System Temp directory
    And the Author config of repository "repo1" is set

  @FileRepository
  Scenario: Verify XML fomratted response of Init with URL Form encoded request parameters and Author
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init" with a URL encoded Form containing a parentDirectory parameter and Author
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"
    And the parent directory of repository "repo1" equals System Temp directory
    And the Author config of repository "repo1" is set

  @Status400
  Scenario: Verify Init with unsupported MediaType does not create a repository with defualt settings
    Given There is an empty multirepo server
    When I call "PUT /repos/repo1/init.json" with an unsupported media type
    Then the response status should be '400'
    And there should be no "repo1" created
