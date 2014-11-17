CREATE TABLE dbo.geogig_sqlserver_test (
    gid integer NOT NULL PRIMARY KEY,
    geom geometry,
    name character varying(35)
);


INSERT INTO dbo.geogig_sqlserver_test (gid, geom, name) VALUES
(1,geometry:: STGeomFromText('POINT(-122.862936890879 42.3397044113681)', 4326),'feature1');
INSERT INTO dbo.geogig_sqlserver_test (gid, geom, name) VALUES
(2,geometry:: STGeomFromText('POINT(-123.862936890879 41.3397044113681)', 4326),'feature2');
INSERT INTO dbo.geogig_sqlserver_test (gid, geom, name) VALUES
(3,geometry:: STGeomFromText('POINT(-121.862936890879 40.3397044113681)', 4326),'feature3');