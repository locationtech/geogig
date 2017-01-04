Feature: "format-patch" command
    In order to share a diff
    As a Geogig User
    I want to create a patch file
    
  Scenario: I try to create a patch file
    Given I have a repository
      And I stage 6 features
      And I modify a feature
     When I run the command "format-patch -f ${currentdir}/some_file"
     Then it should exit with zero exit code
  
  Scenario: I try to create a patch without specifying the file
    Given I have a repository
      And I stage 6 features
      And I modify a feature
     When I run the command "format-patch"
     Then the response should contain "Patch file not specified"
   
  Scenario: I try to create a patch when there are no changes
    Given I have a repository
      And I have several commits
     When I run the command "format-patch -f ${currentdir}/some_file"
     Then the response should contain "No differences found"
     
  Scenario: I try to create a patch with too many commit arguments
    Given I have a repository
      And I have several commits
     When I run the command "format-patch commit1 commit2 commit3"
     Then the response should contain "Commit list is too long"
     
  Scenario: I try to create a patch between working tree and index, for a single modified tree
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "format-patch --path Points -f ${currentdir}/some_file"
     Then it should exit with zero exit code
     