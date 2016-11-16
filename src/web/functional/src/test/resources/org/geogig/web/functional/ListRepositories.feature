@RepositoryManagement
Feature: List repositories
    In order to find out which repsitories are being served,
    As a Geogig Web API User,
    I want to get a list of available repositories

  Scenario: List repositories on a server with no repositories
    Given There is an empty multirepo server
     When I call "GET /repos/"
     Then the response status should be '200'
      And the response ContentType should be "application/xml"
      And the xml response should not contain "/repos/repo"

  Scenario: Get list of repositories in default format
    Given There is a default multirepo server
     When I call "GET /repos/"
     Then the response status should be '200'
      And the response ContentType should be "application/xml"
      And the xml response should contain "/repos/repo/name" 2 times

  Scenario: List repositories on a server with no repositories, JSON requested response
    Given There is an empty multirepo server
     When I call "GET /repos.json/"
     Then the response status should be '200'
      And the response ContentType should be "application/json"
      And the json response "repos" contains an empty "repo" array

  Scenario: Get list of repositories in default format, JSON requested response
    Given There is a default multirepo server
     When I call "GET /repos.json/"
     Then the response status should be '200'
      And the response ContentType should be "application/json"
      And the json response "repos.repo" should contain "name" 2 times
