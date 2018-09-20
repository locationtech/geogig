Feature: GeoGig DataStore Feature read/write validation
  The GeoGig DataStore is the integration point between GeoServer/OGC services
  and GeoGig repositories. These scenarios are meant to ensure data/feature
  integrity via interactions with the DataStore.

   Scenario: Ensure Point Features can be retrieved from a DataStore
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread

   Scenario: Ensure Polygon Features can be retrieved from a DataStore
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread

   Scenario: Ensure Point Features can be retrieved from a DataStore with an indexed repo
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread

   Scenario: Ensure Polygon Features can be retrieved from a DataStore with an indexed repo
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread

   Scenario: Ensure indexed Point Features match non-indexed Features
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      And datastore "dataStore1" and datastore "dataStore2" both have the same features

   Scenario: Ensure indexed Polygon Features match non-indexed Features
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      And datastore "dataStore1" and datastore "dataStore2" both have the same features

   Scenario: Ensure Point Features can be retrieved from a DataStore after editing
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature

   Scenario: Ensure Polygon Features can be retrieved from a DataStore after editing
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature

   Scenario: Ensure Point Features can be retrieved from a DataStore after editing with index
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature

   Scenario: Ensure Polygon Features can be retrieved from a DataStore after editing with index
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature

   Scenario: Ensure edited indexed Point Features match edited non-indexed Features
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      And I make an edit to "dataStore1"
      And I make the same edit to "dataStore2"
      Then datastore "dataStore1" and datastore "dataStore2" both have the same features

   Scenario: Ensure edited indexed Polygon Features match edited non-indexed Features
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      And I make an edit to "dataStore1"
      And I make the same edit to "dataStore2"
      Then datastore "dataStore1" and datastore "dataStore2" both have the same features

   Scenario: Ensure Point Features can be edited concurrently
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make 20 edits to "dataStore1" using 4 edit threads
      Then datastore "dataStore1" has the edited features

   Scenario: Ensure Polygon Features can be edited concurrently
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make 20 edits to "dataStore1" using 4 edit threads
      Then datastore "dataStore1" has the edited features

   Scenario: Ensure Point Features can be edited concurrently with concurrent reads
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      And I make 20 edits to "dataStore1" using 4 edit threads while using 20 read threads and 8 reads per thread
      Then datastore "dataStore1" has the edited features

   Scenario: Ensure Polygon Features can be edited concurrently with concurrent reads
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      And I make 20 edits to "dataStore1" using 4 edit threads while using 20 read threads and 8 reads per thread
      Then datastore "dataStore1" has the edited features
