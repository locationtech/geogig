Feature: GeoGig DataStore Layer Dimension validation
  The GeoGig DataStore is the integration point between GeoServer/OGC services
  and GeoGig repositories. These scenarios are meant to ensure data/feature
  integrity when layers utilize time/elevation dimensions.

   Scenario: Ensure Point Features with Time dimensions can be retrieved from a DataStore
      Given I am working with the "pointTime" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 1 threads and 1 reads per thread
      And features in "dataStore1" should contain a Time attribute

   Scenario: Ensure Point Features with Time dimensions can be retrieved from a DataStore with indexes
      Given I am working with the "pointTime" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1" with extra attributes "dp"
      Then I should be able to retrieve data from "dataStore1" using 1 threads and 1 reads per thread
      And features in "dataStore1" should contain a Time attribute

   Scenario: Ensure Point Features with NULL Time dimension values can be retrieved from a DataStore with indexes
      Given I am working with the "pointTime" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1" with extra attributes "dp"
      And I edit a time dimension attribute value in "dataStore1" to be NULL
      Then I should be able to retrieve data from "dataStore1" using 1 threads and 1 reads per thread
      And the edited feature in "dataStore1" should contain a NULL Time attribute
