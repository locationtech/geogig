@RepositoryManagement
Feature: Delete Repository
	Deleting a repository through the web API is a non reversible operation.
  * In order to avoid accidental deletion of repositories, it is a two-step process:
  * first a GET call to "/{repository}/delete" returns an automatically generated token with the format:
  * <response><success>true</success><token>d713df9c703733e2</token></response>.
  * To actually delete the repository, a HTTP DELETE method call to "/{repository}?token={token}" must be issued, with a valid and non expired token.
  * An attempt to delete a non existent repository, results in a 404 "Not found" error code.
  * A successfull DELETE operation returns a 200 status code,
  * the XML response body is <deleted>true</deleted>, the JSON response body is '{"deleted":true}'
  
  Scenario: Requesting delete token with wrong HTTP Method issues 405 "Method not allowed"
    Given There is a default multirepo server
     When I call "POST /repo1/delete"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  Scenario: Requesting a delete token for a non existent repository issues 404 "Not found"
    Given There is a default multirepo server
     When I call "GET /nonExistentRepo/delete"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"

  Scenario: Try deleting a non existent repository issues 404 "Not found"
    Given There is a default multirepo server
     When I call "DELETE /nonExistentRepo?token=someToken"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "error:No repository to delete."

  Scenario: Succesfully delete a repository
    Given There is a default multirepo server
     When I call "GET /repo2/delete"
     Then the response status should be '200'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/token"
     Then I save the response "/response/token/text()" as "@token"
     When I call "DELETE /repo2?token={@token}"
     Then the response status should be '200'
      And the response ContentType should be "application/xml"
      And the xpath "/deleted/text()" equals "repo2"
      