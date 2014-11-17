Feature: "help" command
    In order to know how to use geogig
    As a Geogig User
    I want to see the description of a command

  Scenario: Show general help, containing only porcelain commands
    Given I have a repository      
     When I run the command "help"
     Then the response should contain "commit"
      And the response should not contain "cat-object"
      And the response should not contain "ls-tree"