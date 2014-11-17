Feature: "diff" command
    In order to know changes made in a repository
    As a Geogig User
    I want to see the existing differences between commits 
     
Scenario: Show diff between working tree and index
    Given I have a repository
      And I stage 6 features      
      And I modify a feature
     When I run the command "diff"
     Then the response should contain "Points/Points.1"       
      And the response should contain "[1.0,1.0] (1.0,2.0)"      
      And the response should contain "1000"
      And the response should contain "1001"
      And the response should contain "StringProp1_1"  
      And the response should contain "StringProp1_1a"
     
Scenario: Show diff between working tree and index, showing only summary 
    Given I have a repository
      And I stage 6 features      
      And I modify a feature
     When I run the command "diff --summary"
     Then the response should contain "Points/Points.1"   
     
Scenario: Show diff between working tree and index, when no changes have been made 
    Given I have a repository
      And I stage 6 features         
     When I run the command "diff"
     Then the response should contain "No differences found"   
     
Scenario: Show diff between working tree and index, for a single modified tree
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff -- Points"
     Then the response should contain "Points/Points.1"   
      And the response should contain "[1.0,1.0] (1.0,2.0)"  
      And the response should contain "1000"
      
Scenario: Show diff between working tree and index, for a single unmodified tree
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff -- Lines"
	 Then the response should contain "No differences found"   
      
Scenario: Show diff using too many commit refspecs
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff commit1 commit2 commit3"
	 Then the response should contain "Commit list is too long"  
	  And it should exit with non-zero exit code 
	 
Scenario: Show diff using a wrong commit refspec
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff wrongcommit"
	 Then the response should contain "Refspec wrongcommit does not resolve to a tree"
	  And it should exit with non-zero exit code  	 
     
Scenario: Show diff between working tree and index, for a single modified tree, showing only summary
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff -- Points --summary"
     Then the response should contain "Points/Points.1"  
      And the response should not contain "POINT (1 1)"
      And the response should not contain "1000"
      
Scenario: Show diff between working tree and index, for a single feature whose feature type has changed
    Given I have a repository
      And I stage 6 features   
      And I modify a feature type         
     When I run the command "diff -- Points/Points.1"
     Then the response should contain "extra: [MISSING] -> "
     And the response should contain "ExtraString"  
            
     