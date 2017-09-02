@Commands @Config
Feature: Config
  The config command allows a user to get and set config values and is supported through the "/repos/{repository}/config" endpoint
  The command must be executed using the HTTP GET or POST methods

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "DELETE /repos/repo1/config"
     Then the response status should be '405'
      And the response allowed methods should be "GET,POST"
      
  @Status404
  Scenario: Config outside of a repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/repo1/config"
     Then the response status should be '404'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" equals "Repository not found."
      
  @Status400
  Scenario: Config POST without specifying a key issues a 400 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/config"
     Then the response status should be '400'
      And the xpath "/response/error/text()" contains "You must specify the key when setting a config key."
      
  @Status400
  Scenario: Config POST without specifying a value issues a 400 status code
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/config?name=user.name"
     Then the response status should be '400'
      And the xpath "/response/error/text()" contains "You must specify the value when setting a config key."
      
  Scenario: Config POST with a name and value in the url sets the config entry and GET retrieves the set value
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/config?name=user.name&value=myUser"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/config?name=user.name"
     Then the response status should be '200'
      And the xpath "/response/value/text()" equals "myUser"
      
  Scenario: Config POST with a name and value as json sets the config entry and GET retrieves the set value
    Given There is an empty repository named repo1
     When I "POST" content-type "application/json" to "/repos/repo1/config" with
       """
       {
         "name":"user.name",
         "value":"myUser"
       }
       """
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/config?name=user.name"
     Then the response status should be '200'
      And the xpath "/response/value/text()" equals "myUser"
      
  Scenario: Config POST with a name and value as xml sets the config entry and GET retrieves the set value
    Given There is an empty repository named repo1
     When I "POST" content-type "application/xml" to "/repos/repo1/config" with
       """
       <params>
         <name>user.name</name>
         <value>myUser</value>
       </params>
       """
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
     When I call "GET /repos/repo1/config?name=user.name"
     Then the response status should be '200'
      And the xpath "/response/value/text()" equals "myUser"
      
  Scenario: Config GET without a name will list all config entries
    Given There is an empty repository named repo1
     When I call "POST /repos/repo1/config?name=config.value1&value=myValue1"
      And I call "POST /repos/repo1/config?name=config.value2&value=myValue2"
     When I call "GET /repos/repo1/config"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the response body should contain "config.value1"
      And the response body should contain "myValue1"
      And the response body should contain "config.value2"
      And the response body should contain "myValue2"