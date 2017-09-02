@Commands @Statistics
Feature: Statistics
  The Statistics command allows a user to summarize the changes made to a branch and is supported through the "/repos/{repository}/statistics" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/statistics"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status404
  Scenario: Statistics outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/statistics"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  Scenario: Statistics summarizes the changes made to the repository
    Given There is a default multirepo server
     When I call "GET /repos/repo1/statistics"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Statistics/FeatureTypes/FeatureType" 3 times
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that contains "Points"
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that contains "Lines"
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that contains "Polygons"
      And the xpath "/response/Statistics/FeatureTypes/totalFeatureTypes/text()" equals "3"
      And the xpath "/response/Statistics/FeatureTypes/totalFeatures/text()" equals "9"
      And the xpath "/response/Statistics/latestCommit/id/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Statistics/latestCommit/message/text()" contains "merge branch branch2 onto master"
      And the xpath "/response/Statistics/firstCommit/id/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Statistics/firstCommit/message/text()" contains "point1, line1, poly1"
      And the xpath "/response/Statistics/Authors/Author/name/text()" equals "geogigUser"
      And the xpath "/response/Statistics/Authors/Author/email/text()" equals "repo1_Owner@geogig.org"
      And the xpath "/response/Statistics/Authors/totalAuthors/text()" equals "1"
      
  Scenario: Statistics summarizes the changes since a particular timestamp up to a specific commit
    Given There is a default multirepo server
     When I call "GET /repos/repo1/statistics?since=0&branch=master~1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Statistics/FeatureTypes/FeatureType" 3 times
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that equals "Points"
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/numFeatures/text()" that equals "2"
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that equals "Lines"
      And there is an xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" that equals "Polygons"
      And the xpath "/response/Statistics/FeatureTypes/totalFeatureTypes/text()" equals "3"
      And the xpath "/response/Statistics/FeatureTypes/totalFeatures/text()" equals "6"
      And the xpath "/response/Statistics/latestCommit/id/text()" equals "{@ObjectId|repo1|master~1}"
      And the xpath "/response/Statistics/latestCommit/message/text()" contains "merge branch branch1 onto master"
      And the xpath "/response/Statistics/firstCommit/id/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Statistics/firstCommit/message/text()" contains "point1, line1, poly1"
      And the xpath "/response/Statistics/Authors/Author/name/text()" equals "geogigUser"
      And the xpath "/response/Statistics/Authors/Author/email/text()" equals "repo1_Owner@geogig.org"
      And the xpath "/response/Statistics/Authors/totalAuthors/text()" equals "1"
      
  Scenario: Statistics can summarize the changes made to a specific path
    Given There is a default multirepo server
     When I call "GET /repos/repo1/statistics?path=Points"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Statistics/FeatureTypes/FeatureType" 1 times
      And the xpath "/response/Statistics/FeatureTypes/FeatureType/name/text()" equals "Points"
      And the xpath "/response/Statistics/FeatureTypes/FeatureType/numFeatures/text()" equals "3"
      And the xpath "/response/Statistics/latestCommit/id/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Statistics/latestCommit/message/text()" contains "merge branch branch2 onto master"
      And the xpath "/response/Statistics/firstCommit/id/text()" equals "{@ObjectId|repo1|master~2}"
      And the xpath "/response/Statistics/firstCommit/message/text()" contains "point1, line1, poly1"
      And the xpath "/response/Statistics/Authors/Author/name/text()" equals "geogigUser"
      And the xpath "/response/Statistics/Authors/Author/email/text()" equals "repo1_Owner@geogig.org"
      And the xpath "/response/Statistics/Authors/totalAuthors/text()" equals "1"