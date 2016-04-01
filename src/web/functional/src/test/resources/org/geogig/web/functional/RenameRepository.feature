@RepositoryManagement
Feature: Rename repository
  Repositories being served throught the Web API are served through the "/<repository name>" entry point.
  The name of a repository is unique across a Web API instance.
  Renaming a repository is done through a "POST /<repository name>/rename?name={new name}" call.

  Scenario: Calling rename to a non existing repo issues 404 "Not Found"
    Given There is an empty multirepo server
     When I call "POST /nonExistingRepo/rename?name=renamedRepo"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"

  Scenario: Calling rename on a repo using the wrong HTTP method issues 405 "Method not allowed"
    Given There is a default multirepo server
     When I call "GET /repo1/rename?name=renamedRepo"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
      

  Scenario: Trying to assign a duplicated name to a repo issues 400 "Bad request"
    Given There is a default multirepo server
     When I call "POST /repo1/rename?name=repo2"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "A repository with that name already exists."

  Scenario: Renaming a repository returns a link to the new location issues 301 "Moved permanently"
    Given There is a default multirepo server
     When I call "POST /repo1/rename?name=repo1Renamed"
     Then the response status should be '301'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/repo/name/text()" equals "repo1Renamed"
      And the xpath "/response/repo/atom:link/@href" equals "/repo1Renamed.xml"
