Feature: "config" command
    In order to configure geogig
    As a Geogig User
    I want to get and set global settings as well as repository settings on a directory of my choice

  Scenario: Try to set a config value in the current empty directory
    Given I am in an empty directory
     When I run the command "config testing.key value"
     Then it should answer "The config location is invalid"
      And it should exit with non-zero exit code

  Scenario: Try to get a local config value in the current empty directory
    Given I am in an empty directory
     When I run the command "config --get --local testing.key"
     Then it should answer "the config location is invalid"
      And it should exit with non-zero exit code

  Scenario: Try to list local variables in the current empty directory
    Given I am in an empty directory
     When I run the command "config --list --local"
     Then it should answer "the config location is invalid"
      And it should exit with non-zero exit code
     
  Scenario: Try to get a config value in the current empty directory
    Given I am in an empty directory
     When I run the command "config --get testing.key"
     Then it should answer ""

  Scenario: Try to set and get a global config value in the current empty directory
    Given I am in an empty directory
     When I run the command "config --global testing.global true"
      And I run the command "config --global --get testing.global"
     Then it should answer "true"
     
  Scenario: Try to set and get a config value in the current repository
    Given I have a repository
     When I run the command "config testing.local true"
      And I run the command "config --get testing.local"
     Then it should answer "true"
     When I run the command "config --local --get testing.local"
     Then it should answer "true"
          
  Scenario: Try to set and get a multi-word config value in the current repository
    Given I have a repository
     When I run the command "config testing.local test word"
      And I run the command "config --get testing.local"
     Then it should answer "test word"
     When I run the command "config --local --get testing.local"
     Then it should answer "test word"
     
  Scenario: Try to get a config value that doesn't exist
    Given I have a repository
     When I run the command "config --global --get doesnt.exist"
     Then it should answer ""
     When I run the command "config --get doesnt.exist"
     Then it should answer ""
	 When I run the command "config --local --get doesnt.exist"
	 Then it should answer ""
	 	 
  Scenario: Try to get a config value without specifying key
    Given I have a repository
     When I run the command "config --global --get"
     Then it should answer "No section or name was provided"
     When I run the command "config --get"
     Then it should answer "No section or name was provided"
     When I run the command "config --local --get"
     Then it should answer "No section or name was provided"
      And it should exit with non-zero exit code
     
  Scenario: Try to get a config value using malformed key
    Given I have a repository
     When I run the command "config --global --get test"
     Then it should answer "The section or key is invalid"
     When I run the command "config --get test"
     Then it should answer "The section or key is invalid"
     When I run the command "config --local --get test"
     Then it should answer "The section or key is invalid"
      And it should exit with non-zero exit code
     
  Scenario: Try to get a config value using the alternate syntax 
    Given I have a repository
     When I run the command "config --global section.key value1"
      And I run the command "config --global section.key"
     Then it should answer "value1"
     When I run the command "config section.key value2"
      And I run the command "config section.key"
     Then it should answer "value2"
     When I run the command "config --local section.key"
     Then it should answer "value2"
        
  Scenario: Try to set, unset, and get a config value in the current repository
    Given I have a repository
     When I run the command "config testing.local true"
      And I run the command "config --unset testing.local"
      And I run the command "config --get testing.local"
     Then it should answer ""
     When I run the command "config testing.local true"
      And I run the command "config --unset testing.local"
      And I run the command "config --local --get testing.local"
     Then it should answer ""
          
  Scenario: Try to set, unset, and get a config value globally
    Given I have a repository
     When I run the command "config --global testing.local true"
      And I run the command "config --global --unset testing.local"
      And I run the command "config --global --get testing.local"
     Then it should answer "" 
     
  Scenario: Try to unset a config value that doesn't exist
    Given I have a repository
     When I run the command "config --global --unset testing.local"
      And I run the command "config --global --get testing.local"
     Then it should answer ""
     When I run the command "config --unset testing.local"
      And I run the command "config --local --get testing.local"
     Then it should answer ""
     When I run the command "config --unset testing.local"
      And I run the command "config --get testing.local"
     Then it should answer ""
     
  Scenario: Try to unset and get in the same config command
    Given I have a repository
     When I run the command "config --unset --get testing.local"
     Then it should answer "Tried to use more than one action at a time"
      And it should exit with non-zero exit code
     
  Scenario: Remove a section from the current repository
    Given I have a repository
     When I run the command "config testing.local true"
      And I run the command "config testing.local2 false"
      And I run the command "config --remove-section testing"
      And I run the command "config --get testing.local"
     Then it should answer ""
     When I run the command "config --get testing.local2"
     Then it should answer ""
     
  Scenario: Remove a section globally
    Given I have a repository
     When I run the command "config --global testing.local true"
      And I run the command "config --global testing.local2 false"
      And I run the command "config --global --remove-section testing"
      And I run the command "config --global --get testing.local"
     Then it should answer ""
     When I run the command "config --global --get testing.local2"
     Then it should answer ""
     
  Scenario: Try to remove a section that doesn't exist
    Given I have a repository
     When I run the command "config --remove-section somerandomsection"
     Then it should answer "Could not find a section with the name provided"
     When I run the command "config --global --remove-section somerandomsection"
     Then it should answer "Could not find a section with the name provided"
      And it should exit with non-zero exit code  
       
  Scenario: Add 2 config values and list them
    Given I have a repository
     When I run the command "config testing.local true"
      And I run the command "config testing.local2 false"
      And I run the command "config --list"
     Then the response should contain "testing.local=true"
      And the response should contain "testing.local2=false"
     When I run the command "config testing.local3 true"
      And I run the command "config testing.local4 false"
      And I run the command "config --list --local"
     Then the response should contain "testing.local=true"
      And the response should contain "testing.local2=false"  
      And the response should contain "testing.local3=true"
      And the response should contain "testing.local4=false"    
      
  Scenario: Specify root URI and set a global config option
    Given I have a repository
     When I run the command "config --rootUri ${rootRepoURI} --global testing.global true"
      And I run the command "config testing.local true"
      And I run the command "config --rootUri ${rootRepoURI} --list"
     Then the response should contain "testing.global=true"
      And the response should not contain "testing.local=true"
      
  Scenario: Try to add a local config value when using a root URI
    Given I have a repository
     When I run the command "config --rootUri ${rootRepoURI} testing.local true"
     Then it should answer "The config location is invalid"
     
  Scenario: Try to use both global and local in the same config command
    Given I have a repository
     When I run the command "config --local --global"
     Then the response should contain "Usage:"
     
  Scenario: Try to use the config command with no arguments
    Given I have a repository
     When I run the command "config"
     Then the response should contain "Usage:"