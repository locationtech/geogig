SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

CREATE TABLE geogig_pg_test (
    gid integer DEFAULT nextval(1) NOT NULL,
    geom geometry(Point),
    name character varying(35)
);


ALTER TABLE public.geogig_pg_test OWNER TO postgres;

INSERT INTO geogig_pg_test (gid, geom, name) VALUES
(1,'0101000000B435A75B3AB75EC00391246F7B2B4540','feature1');
INSERT INTO geogig_pg_test (gid, geom, name) VALUES
(2,'0101000000B435A75B3AB75EC00391246F7B2B4540','feature2');
INSERT INTO geogig_pg_test (gid, geom, name) VALUES
(3,'0101000000B435A75B3AB75EC00391246F7B2B4540','feature3');

ALTER TABLE ONLY geogig_pg_test
    ADD CONSTRAINT geogig_pg_test_pkey PRIMARY KEY (gid);