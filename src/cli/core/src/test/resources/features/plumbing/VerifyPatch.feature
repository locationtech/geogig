Feature: "verify-patch" command
    In order to ensure I have a valid patch
    As a Geogig User
    I want to verify the patch
    
Scenario: I try to verify a patch without specifying a file
    Given I have a repository
     When I run the command "verify-patch"
     Then the response should contain "No patch file specified"

Scenario: I try to verify multiple files
    Given I have a repository
     When I run the command "verify-patch file1 file2"
     Then the response should contain "Only one single patch file accepted"
     
Scenario: I try to verify a patch with a bad file path
    Given I have a repository
     When I run the command "verify-patch file1"
     Then the response should contain "Patch file cannot be found"
  
Scenario: I have a patch file that contains rejects
    Given I have a repository
      And I have a patch file
     When I run the command "verify-patch ${currentdir}/test.patch"
     Then the response should contain "Patch cannot be applied"
      And the response should contain 10 lines

Scenario: I have a patch file that is valid
    Given I have a repository
      And I stage 6 features
      And I have a patch file
     When I run the command "verify-patch ${currentdir}/test.patch"
     Then the response should contain "Patch can be applied"
    