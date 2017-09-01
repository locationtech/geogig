# Copyright (c) 2017 Boundless and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Distribution License v1.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/org/documents/edl-v10.html
#
# Contributors:
# Erik Merkle (Boundless) - initial implementation

@CreateRepository @MissingBackend
Feature: Tests Init request behavior when certain repository backends backend are not available

  @Status400
  Scenario: Init request for RocksDB repo with RocksDB backend missing
    Given There is an empty multirepo server
    And I have disabled backends: "Directory"
    When I "PUT" content-type "application/json" to "/repos/repo1/init" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" contains "No repository initializer found capable of handling this kind of URI: file:/"
  @Status400
  Scenario: Init request for RocksDB repo with RocksDB backend missing, JSON response
    Given There is an empty multirepo server
    And I have disabled backends: "Directory"
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json response "response.error" should contain "No repository initializer found capable of handling this kind of URI: file:/"
  @Status400
  Scenario: Init request for PostgreSQL repo with PostgreSQL backend missing
    Given There is an empty multirepo server
    And I have disabled backends: "PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init" with
      """
      {
        "dbName":"database",
        "dbPassword":"password"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" contains "No repository initializer found capable of handling this kind of URI: postgresql:/"
  @Status400
  Scenario: Init request for PostgreSQL repo with PostgreSQL backend missing, JSON response
    Given There is an empty multirepo server
    And I have disabled backends: "PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "dbName":"database",
        "dbPassword":"password"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json response "response.error" should contain "No repository initializer found capable of handling this kind of URI: postgresql:/"

  Scenario: Init request for RocksDB repo with PostgreSQL backend missing
    Given There is an empty multirepo server
    And I have disabled backends: "PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '201'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "true"
    And the xpath "/response/repo/name/text()" equals "repo1"
    And the xpath "/response/repo/atom:link/@href" contains "/repos/repo1.xml"

  Scenario: Init request for RocksDB repo with PostgreSQL backend missing, JSON response
    Given There is an empty multirepo server
    And I have disabled backends: "PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '201'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "true"
    And the json object "response.repo.name" equals "repo1"
    And the json object "response.repo.href" ends with "/repos/repo1.json"
  @Status400
  Scenario: Init request for RocksDB repo with RocksDB and PostgreSQL backends missing
    Given There is an empty multirepo server
    And I have disabled backends: "Directory, PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" contains "No repository initializer found capable of handling this kind of URI: file:/"
  @Status400
  Scenario: Init request for RocksDB repo with RocksDB and PostgreSQL backends missing, JSON response
    Given There is an empty multirepo server
    And I have disabled backends: "Directory, PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "parentDirectory":"{@systemTempPath}"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json response "response.error" should contain "No repository initializer found capable of handling this kind of URI: file:/"
  @Status400
  Scenario: Init request for PostgreSQL repo with RocksDB and PostgreSQL backends missing
    Given There is an empty multirepo server
    And I have disabled backends: "Directory, PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init" with
      """
      {
        "dbName":"database",
        "dbPassword":"password"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/xml"
    And the xpath "/response/success/text()" equals "false"
    And the xpath "/response/error/text()" contains "No repository initializer found capable of handling this kind of URI: postgresql:/"
  @Status400
  Scenario: Init request for PostgreSQL repo with RocksDB and PostgreSQL backends missing, JSON response
    Given There is an empty multirepo server
    And I have disabled backends: "Directory, PostgreSQL"
    When I "PUT" content-type "application/json" to "/repos/repo1/init.json" with
      """
      {
        "dbName":"database",
        "dbPassword":"password"
      }
      """
    Then the response status should be '400'
    And the response ContentType should be "application/json"
    And the json object "response.success" equals "false"
    And the json response "response.error" should contain "No repository initializer found capable of handling this kind of URI: postgresql:/"
   
