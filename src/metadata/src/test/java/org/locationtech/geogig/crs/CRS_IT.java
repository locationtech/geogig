package org.locationtech.geogig.crs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Set;
import java.util.function.Function;

import org.geotools.util.factory.FactoryRegistryException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.google.common.base.Stopwatch;

public class CRS_IT {

    public @Rule TestName testName = new TestName();

    public @Before void before() {
        System.err.println("# Test: " + testName.getMethodName());
    }

    public @Test void testDecode() {
        CoordinateReferenceSystem crs = CRS.decode("EPSG:4326");
        assertNotNull(crs);
        assertEquals("EPSG:4326", crs.getSrsIdentifier());
        assertNotNull(crs.getWKT());
    }

    public @Test void testGigPerf() throws FactoryRegistryException, FactoryException {
        Set<String> authorityCodes = CRS.getAuthorityCodes();
        testPerf(authorityCodes, CRS::decode);
    }

    public @Test void testGTPerf() throws NoSuchAuthorityCodeException, FactoryException {
        Set<String> authorityCodes = CRS.getAuthorityCodes();
        testPerf(authorityCodes, t -> {
            try {
                return org.geotools.referencing.CRS.decode(t);
            } catch (FactoryException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void testPerf(Set<String> authorityCodes, Function<String, Object> loader) {
        Stopwatch sw = Stopwatch.createStarted();
        authorityCodes.forEach(code -> assertNotNull(loader.apply(code)));
        System.err.printf("First run: loaded %d CRS's in %s\n", authorityCodes.size(), sw.stop());
        sw.reset().start();
        authorityCodes.forEach(code -> assertNotNull(loader.apply(code)));
        System.err.printf("Second run: loaded %d CRS's in %s\n", authorityCodes.size(), sw.stop());
    }
}
