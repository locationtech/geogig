@Commands @Remote
Feature: Remote
  The remote command allows a user to manage the remotes of a repository and is supported through the "/repos/{repository}/remote" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/remote"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Remote outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/remote"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Supplying the list parameter will list all remotes
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?list=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Remote" 2 times
      And the response body should contain "repo1"
      And the response body should contain "repo2"
      
  Scenario: Supplying the list parameter with verbose will list more detail of all remotes
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo2/remote?list=true&verbose=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Remote/name/text()" equals "origin"
      And the xml response should contain "/response/Remote/url" 1 times
      
  @Status400
  Scenario: Supplying the ping parameter without a remote name issues a 400 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?ping=true"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "MISSING_NAME"
      
  Scenario: Pinging a reachable remote returns as successful
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?remoteName=repo1&ping=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/ping/success/text()" equals "true"
      
  Scenario: Pinging a nonexistent remote returns as not successful
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?remoteName=nonexistent&ping=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/ping/success/text()" equals "false"
      
  @Status500
  Scenario: Supplying the remove parameter without a remote name issues a 500 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?remove=true"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No remote was specified."
      
  @Status400
  Scenario: Removing a nonexistent remote issues a 400 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?remove=true&remoteName=nonexistent"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "REMOTE_NOT_FOUND"
      
  Scenario: Removing a valid remote name removes the remote
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?remove=true&remoteName=repo1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/name/text()" equals "repo1"
     When I call "GET /repos/repo3/remote?list=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Remote" 1 times
      And the response body should not contain "repo1"
      And the response body should contain "repo2"
      
  @Status500
  Scenario: Supplying the update parameter without a remote name issues a 500 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?update=true"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No remote was specified."
      
  @Status500
  Scenario: Supplying the update parameter without a remote url issues a 500 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?update=true&remoteName=repo1"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No URL was specified."
      
  Scenario: Supplying a new url with the update parameter updates a remote
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo2/remote?update=true&remoteName=origin&remoteURL=newUrl"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/name/text()" equals "origin"
     When I call "GET /repos/repo2/remote?list=true&verbose=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/Remote/name/text()" equals "origin"
      And the xpath "/response/Remote/url/text()" equals "newUrl"
      
  Scenario: Supplying a new name with the update parameter renames a remote
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?update=true&remoteName=repo1&remoteURL=repo1url&newName=renamed"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/name/text()" equals "renamed"
     When I call "GET /repos/repo3/remote?list=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Remote" 2 times
      And the response body should contain "renamed"
      And the response body should contain "repo2"
      
  @Status500
  Scenario: Supplying the add parameter without a remote name issues a 500 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?add=true"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No remote was specified."
      
  @Status500
  Scenario: Supplying the add parameter without a remote url issues a 500 status code
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?add=true&remoteName=newRemote"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "No URL was specified."
      
  Scenario: Supplying add with valid parameters adds a new remote
    Given There is a default multirepo server with remotes
     When I call "GET /repos/repo3/remote?add=true&remoteName=newRemote&remoteURL=newUrl"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/name/text()" equals "newRemote"
     When I call "GET /repos/repo3/remote?list=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Remote" 3 times
      And the response body should contain "repo1"
      And the response body should contain "repo2"
      And the response body should contain "newRemote"