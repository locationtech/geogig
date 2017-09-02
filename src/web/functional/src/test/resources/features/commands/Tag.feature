@Commands @Tag
Feature: Tag
  The Tag command allows a user to create, list, and delete tags and is supported through the "/repos/{repository}/tag" endpoint
  The command must be executed using the HTTP GET, POST, or DELETE methods

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/tag"
     Then the response status should be '405'
      And the response allowed methods should be "GET,POST,DELETE"
      
  @Status404
  Scenario: Tag outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/tag"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status500
  Scenario: Issuing a POST request to tag without a name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/tag"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "You must specify list or delete, or provide a name, message, and commit for the new tag."

  @Status500
  Scenario: Issuing a POST request to tag without a commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/tag?name=newTag"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "You must specify a commit to point the tag to."
      
  @Status500
  Scenario: Issuing a POST request to tag with an invalid commit issues a 500 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/tag?name=newTag&commit=nonexistent"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "'nonexistent' could not be resolved."
      
  Scenario: Issuing a POST request to tag with valid parameters creates a tag
    Given There is a default multirepo server
     When I call "POST /repos/repo1/tag?name=newTag&commit=master&message=My%20Tag"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Tag/id" 1 times
      And the xpath "/response/Tag/commitid/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/Tag/name/text()" equals "newTag"
      And the xpath "/response/Tag/message/text()" equals "My Tag"
      And the xpath "/response/Tag/tagger/name/text()" equals "geogigUser"
      And the xpath "/response/Tag/tagger/email/text()" equals "repo1_Owner@geogig.org"
      And the xml response should contain "/response/Tag/tagger/timestamp" 1 times
      And the xml response should contain "/response/Tag/tagger/timeZoneOffset" 1 times
      
  Scenario: Issuing a GET request to tag lists all tags
    Given There is a default multirepo server
      And I call "POST /repos/repo1/tag?name=tag1&commit=master&message=My%20Tag%201"
      And I call "POST /repos/repo1/tag?name=tag2&commit=branch1&message=My%20Tag%202"
     When I call "GET /repos/repo1/tag"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Tag" 2 times
      And there is an xpath "/response/Tag/commitid/text()" that equals "{@ObjectId|repo1|master}"
      And there is an xpath "/response/Tag/name/text()" that equals "tag1"
      And there is an xpath "/response/Tag/message/text()" that equals "My Tag 1"
      And there is an xpath "/response/Tag/commitid/text()" that equals "{@ObjectId|repo1|branch1}"
      And there is an xpath "/response/Tag/name/text()" that equals "tag2"
      And there is an xpath "/response/Tag/message/text()" that equals "My Tag 2"
      
  @Status500
  Scenario: Issuing a DELETE request to tag without a name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "DELETE /repos/repo1/tag"
     Then the response status should be '500'
      And the xpath "/response/error/text()" equals "You must specify the tag name to delete."
      
  Scenario: Issuing a DELETE request to tag deletes the tag
    Given There is a default multirepo server
      And I call "POST /repos/repo1/tag?name=tag1&commit=master&message=My%20Tag%201"
      And I call "POST /repos/repo1/tag?name=tag2&commit=branch1&message=My%20Tag%202"
     When I call "DELETE /repos/repo1/tag?name=tag1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/DeletedTag/id" 1 times
      And the xpath "/response/DeletedTag/commitid/text()" equals "{@ObjectId|repo1|master}"
      And the xpath "/response/DeletedTag/name/text()" equals "tag1"
      And the xpath "/response/DeletedTag/message/text()" equals "My Tag 1"
      And the xpath "/response/DeletedTag/tagger/name/text()" equals "geogigUser"
      And the xpath "/response/DeletedTag/tagger/email/text()" equals "repo1_Owner@geogig.org"
      And the xml response should contain "/response/DeletedTag/tagger/timestamp" 1 times
      And the xml response should contain "/response/DeletedTag/tagger/timeZoneOffset" 1 times
     When I call "GET /repos/repo1/tag"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Tag/id" 1 times
      And the xpath "/response/Tag/commitid/text()" equals "{@ObjectId|repo1|branch1}"
      And the xpath "/response/Tag/name/text()" equals "tag2"
      And the xpath "/response/Tag/message/text()" equals "My Tag 2"