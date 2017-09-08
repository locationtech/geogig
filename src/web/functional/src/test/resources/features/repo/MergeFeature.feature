@Repo @MergeFeature
Feature: MergeFeature
  The MergeFeature resource provides a method of merging two conflicting features and is supported through the "/repos/{repository}/repo/mergefeature" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/repo/mergefeature"
     Then the response status should be '405'
      And the response allowed methods should be "POST"
  @Status404
  Scenario: MergeFeature outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "POST /repos/repo1/repo/mergefeature"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"
  @Status400
  Scenario: MergeFeature with no json payload issues a 400 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/repo/mergefeature"
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."
  @Status400
  Scenario: MergeFeature with an invalid json payload issues a 400 status code
    Given There is an empty repository named repo1
     When I "POST" content-type "text/plain" to "/repos/repo1/repo/mergefeature" with
      """
      "unexpected format"
      """
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."
  @Status400
  Scenario: MergeFeature without a feature issues a 400 status code
    Given There is an empty repository named repo1
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {}
      """
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."
  @Status400
  Scenario: MergeFeature without an "ours" id issues a 400 status code
    Given There is an empty repository named repo1
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1"
      }
      """
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."
  @Status400
  Scenario: MergeFeature without a "theirs" id issues a 400 status code
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1",
        "ours"="{@ObjectId|repo1|master}"
      }
      """
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."
  @Status400
  Scenario: MergeFeature without any merges issues a 400 status code
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1",
        "ours"="{@ObjectId|repo1|master}",
        "theirs"="{@ObjectId|repo1|branch1}"
      }
      """
     Then the response status should be '400'
      And the response body should contain "Invalid POST data."

  Scenario: MergeFeature builds a new feature from provided merges using ours and theirs (both features are the same)
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1",
        "ours"="{@ObjectId|repo1|master}",
        "theirs"="{@ObjectId|repo1|branch1}",
        "merges"={
          "ip"={"ours"=true},
          "sp"={"theirs"=true},
          "geom"={"ours"=true}
        }
      }
      """
     Then the response status should be '200'
      And the response body should contain "{@ObjectId|repo1|master:Points/Point.1}"
      
  Scenario: MergeFeature builds a new feature from provided merges with custom values (both features are the same)
    Given There is a default multirepo server
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1",
        "ours"="{@ObjectId|repo1|master}",
        "theirs"="{@ObjectId|repo1|branch1}",
        "merges"={
          "ip"={"value"=500},
          "sp"={"value"="new"},
          "geom"={"value"="POINT (1 1)"}
        }
      }
      """
     Then the response status should be '200'
      And the response body should not contain "{@ObjectId|repo1|master:Points/Point.1}"
      And the response body should not contain "{@ObjectId|repo1|branch1:Points/Point.1}"
      And I save the response as "@featureId"
     When I call "GET /repos/repo1/cat?objectid={@featureId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/feature/id/text()" equals "{@featureId}"
      And the xml response should contain "/response/feature/attribute" 3 times
      And the response body should contain "new"
      And the response body should contain "500"
      And the response body should contain "POINT (1 1)"

  Scenario: MergeFeature builds a new feature from provided merges (different versions of the same feature)
    Given There is a default multirepo server
      And I have committed "Point.1_modified" on the "repo1" repo in the "" transaction
     When I "POST" content-type "application/json" to "/repos/repo1/repo/mergefeature" with
      """
      {
        "path"="Points/Point.1",
        "ours"="{@ObjectId|repo1|master}",
        "theirs"="{@ObjectId|repo1|branch1}",
        "merges"={
          "ip"={"ours"=true},
          "sp"={"theirs"=true},
          "geom"={"value"="POINT (1 1)"}
        }
      }
      """
     Then the response status should be '200'
      And the response body should not contain "{@ObjectId|repo1|master:Points/Point.1}"
      And the response body should not contain "{@ObjectId|repo1|branch1:Points/Point.1}"
      And I save the response as "@featureId"
     When I call "GET /repos/repo1/cat?objectid={@featureId}"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/feature/id/text()" equals "{@featureId}"
      And the xml response should contain "/response/feature/attribute" 3 times
      And the response body should contain "1500"
      And the response body should contain "StringProp1_1"
      And the response body should contain "POINT (1 1)"