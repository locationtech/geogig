@Commands @Cat
Feature: Cat
  The cat command allows a user to display the attributes of a repository object and is supported through the "/repos/{repository}/cat" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/cat"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Calling cat without specifying an object id issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/cat"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Required parameter 'objectid' was not provided."
      
  @Status400
  Scenario: Calling cat with an invalid object id issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/cat?objectid=notobjectid"
     Then the response status should be '400'
      And the xpath "/response/error/text()" contains "You must specify a valid non-null ObjectId."
      
  @Status400
  Scenario: Calling cat with a nonexistent object id issues a 400 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/cat?objectid=0123456789012345678901234567890123456789"
     Then the response status should be '400'
      And the xpath "/response/error/text()" contains "The specified ObjectId was not found in the respository."
      
  Scenario: Calling cat on a commit returns details of that commit
    Given There is a default multirepo server
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|master}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/commit/id/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/commit/parents/id[1]/text()" equals "{@ObjectId|repo1|master^1}"
      And the xpath "/response/commit/parents/id[2]/text()" equals "{@ObjectId|repo1|master^2}"
      And the xpath "/response/commit/author/name/text()" equals "geogigUser"
      And the xpath "/response/commit/author/email/text()" equals "repo1_Owner@geogig.org"
      And the xpath "/response/commit/committer/name/text()" equals "geogigUser"
      And the xpath "/response/commit/committer/email/text()" equals "repo1_Owner@geogig.org"
      And the xpath "/response/commit/message/text()" contains "merge branch branch2 onto master"
      
  Scenario: Calling cat on a feature returns details of that feature
    Given There is a default multirepo server
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|master:Points/Point.1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/feature/id/text()" equals "{@ObjectId|repo1|master:Points/Point.1}"
      And the xml response should contain "/response/feature/attribute" 3 times
      And the response body should contain "StringProp1_1"
      And the response body should contain "1000"
      And the response body should contain "POINT (0 0)"
      
  Scenario: Calling cat on a feature tree returns details of that tree
    Given There is a default multirepo server
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|master:Points}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/tree/id/text()" equals "{@ObjectId|repo1|master:Points}"
      And the xpath "/response/tree/size/text()" equals "3"
      And the xpath "/response/tree/numtrees/text()" equals "0"
      And the xml response should contain "/response/tree/feature" 3 times
      And the response body should contain "Point.1"
      And the response body should contain "Point.2"
      And the response body should contain "Point.3"
      
  Scenario: Calling cat on a tag returns details of that tag
    Given There is a default multirepo server
      And There is a tag called "tag1" on the "repo1" repo pointing to "{@ObjectId|repo1|master}" with the "My tag" message
     When I call "GET /repos/repo1/cat?objectid={@ObjectId|repo1|refs/tags/tag1}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/tag/id/text()" equals "{@ObjectId|repo1|refs/tags/tag1}"
      And the xpath "/response/tag/commitid/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/tag/name/text()" equals "tag1"
      And the xpath "/response/tag/message/text()" equals "My tag"      
      And the xpath "/response/tag/tagger/name/text()" equals "geogigUser"
      And the xpath "/response/tag/tagger/email/text()" equals "repo1_Owner@geogig.org"
      
  Scenario: Calling cat on a feature type returns details of that feature type
    Given There is a default multirepo server
     When I call "GET /repos/repo1/cat?objectid={@PointsTypeID}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/featuretype/id/text()" equals "{@PointsTypeID}"
      And the xpath "/response/featuretype/name/text()" equals "http://geogig.org:Points"
      And the xml response should contain "/response/featuretype/attribute" 3 times
      And the response body should contain "sp"
      And the response body should contain "ip"
      And the response body should contain "geom"