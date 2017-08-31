/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.config;

import org.locationtech.geogig.spring.service.LegacyMergeFeatureService;
import org.locationtech.geogig.spring.service.RepositoryInitService;
import org.locationtech.geogig.spring.service.RepositoryListService;
import org.locationtech.geogig.spring.service.RepositoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Spring MVC class for configuring the Web API.
 *
 * @see <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html">
 * The Spring Web MVC Documentation</a> for more.
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "org.locationtech.geogig.spring")
public class GeoGigWebAPISpringConfig {

    // Service Beans
    @Bean
    public RepositoryService getRepositoryService() {
        return new RepositoryService();
    }

    @Bean
    public RepositoryListService getRepositoryListService() {
        return new RepositoryListService();
    }

    @Bean
    public RepositoryInitService getRepositoryInitService() {
        return new RepositoryInitService();
    }

    @Bean
    public LegacyMergeFeatureService getLegacyMergeFeatureService() {
        return new LegacyMergeFeatureService();
    }
}
