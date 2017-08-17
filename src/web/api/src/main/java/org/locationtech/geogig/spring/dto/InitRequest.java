/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.google.common.collect.Maps;

/**
 * Bean for JSON init request.
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class InitRequest {

    // Database config
    @XmlElement
    private String dbHost = "localhost";
    @XmlElement
    private String dbName;
    @XmlElement
    private String dbSchema = "public";
    @XmlElement
    private String dbPassword;
    @XmlElement
    private String dbUser = "postgres";
    @XmlElement
    private int dbPort = 5432;
    // File/Directory config
    @XmlElement
    private String parentDirectory;
    // Author config
    @XmlElement
    private String authorName;
    @XmlElement
    private String authorEmail;

    public String getDbHost() {
        return dbHost;
    }

    public InitRequest setDbHost(String dbHost) {
        this.dbHost = dbHost;
        return this;
    }

    public String getDbName() {
        return dbName;
    }

    public InitRequest setDbName(String dbName) {
        this.dbName = dbName;
        return this;
    }

    public String getDbSchema() {
        return dbSchema;
    }

    public InitRequest setDbSchema(String dbSchema) {
        this.dbSchema = dbSchema;
        return this;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public InitRequest setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
        return this;
    }

    public String getDbUser() {
        return dbUser;
    }

    public InitRequest setDbUser(String dbUser) {
        this.dbUser = dbUser;
        return this;
    }

    public int getDbPort() {
        return dbPort;
    }

    public InitRequest setDbPort(int dbPort) {
        this.dbPort = dbPort;
        return this;
    }

    public String getAuthorName() {
        return authorName;
    }

    public InitRequest setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public InitRequest setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    public String getParentDirectory() {
        return parentDirectory;
    }

    public InitRequest setParentDirectory(String parentDirectory) {
        this.parentDirectory = parentDirectory;
        return this;
    }

    public Map<String, String> getParameters() {
        return Maps.newHashMap();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InitRequest{");
        sb.append("parentDirectory = ").append(parentDirectory)
                .append(", dbHost = ").append(dbHost)
                .append(", dbPort = ").append(dbPort)
                .append(", dbName = ").append(dbName)
                .append(", dbUser = ").append(dbUser)
                .append(", dbPassword = ").append(dbPassword)
                .append(", authorName = ").append(authorName)
                .append(", authoerEmail = ").append(authorEmail);
        return sb.toString();
    }

}
