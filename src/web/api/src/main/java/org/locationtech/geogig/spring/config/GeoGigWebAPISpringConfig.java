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

import java.util.Properties;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.service.LegacyApplyChangesService;
import org.locationtech.geogig.spring.service.LegacyBatchObjectsService;
import org.locationtech.geogig.spring.service.LegacyConsoleService;
import org.locationtech.geogig.spring.service.LegacyFilteredChangesService;
import org.locationtech.geogig.spring.service.LegacyMergeFeatureService;
import org.locationtech.geogig.spring.service.LegacyPushService;
import org.locationtech.geogig.spring.service.LegacySendObjectService;
import org.locationtech.geogig.spring.service.RepositoryInitService;
import org.locationtech.geogig.spring.service.RepositoryListService;
import org.locationtech.geogig.spring.service.RepositoryService;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Spring MVC class for configuring the Web API.
 *
 * @see <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html">
 * The Spring Web MVC Documentation</a> for more.
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "org.locationtech.geogig.spring")
public class GeoGigWebAPISpringConfig extends WebMvcConfigurerAdapter {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("classpath:/org/locationtech/geogig/spring/controller/");
    }

    @Bean
    public InternalResourceViewResolver internalResourceViewResolver() {
        InternalResourceViewResolver internalResourceViewResolver = new InternalResourceViewResolver();
        internalResourceViewResolver.setPrefix("/resources/");
        return internalResourceViewResolver;
    }

    @Bean
    public static PropertyPlaceholderConfigurer getPropertyPlaceholderConfigurer() {
        PropertyPlaceholderConfigurer configurer = new PropertyPlaceholderConfigurer();
        Properties props = new Properties();
        props.setProperty(RepositoryProvider.GEOGIG_ROUTE_PREFIX_PROPERTY, "");
        configurer.setProperties(props);
        return configurer;
    }

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

    @Bean
    public LegacyApplyChangesService getApplyChangesService() {
        return new LegacyApplyChangesService();
    }

    @Bean
    public LegacyFilteredChangesService getLegacyFilteredChangesService() {
        return new LegacyFilteredChangesService();
    }

    @Bean
    public LegacyBatchObjectsService getLegacyBatchObjectsService() {
        return new LegacyBatchObjectsService();
    }

    @Bean
    public LegacySendObjectService getLegacySendObjectService() {
        return new LegacySendObjectService();
    }

    @Bean
    public LegacyPushService getPushService() {
        return new LegacyPushService();
    }

    @Bean
    public LegacyConsoleService getConsoleService() {
        return new LegacyConsoleService();
    }

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
