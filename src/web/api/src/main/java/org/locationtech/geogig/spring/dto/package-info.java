/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
/**
 * Defines the various GeoGig Web API Request and Response bean objects. All Response objects should
 * implement the {@link org.locationtech.geogig.spring.dto.LegacyResponse} interface and provide an
 * implementation for the
 * {@link org.locationtech.geogig.spring.dto.LegacyResponse#encodeInternal(
 * org.locationtech.geogig.web.api.StreamingWriter, org.springframework.http.MediaType,
 * java.lang.String) LegacyResponse.encode}
 * method in order to keep the API responses backward compatible with GeoGig 1.x.
 */
@XmlSchema(xmlns = {@XmlNs(prefix = "atom", namespaceURI = "http://www.w3.org/2005/Atom")},
        attributeFormDefault = QUALIFIED, elementFormDefault = QUALIFIED)
package org.locationtech.geogig.spring.dto;

import static javax.xml.bind.annotation.XmlNsForm.QUALIFIED;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;
