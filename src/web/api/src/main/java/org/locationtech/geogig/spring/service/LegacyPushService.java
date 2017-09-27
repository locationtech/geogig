/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.PushManager;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.BeginPush;
import org.locationtech.geogig.spring.dto.EndPush;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;

/**
 *
 */
@Service("legacyPushService")
public class LegacyPushService extends AbstractRepositoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyPushService.class);

    public BeginPush beginPush(String clientIp, String internalIp) {
        BeginPush beginPush = new BeginPush();
        // make a combined ip address to handle requests from multiple machines in the same
        // external network.
        // e.g.: ext.ern.al.IP.int.ern.al.IP
        String combinedAddress = clientIp + "." + internalIp;
        beginPush.setCombinedAddress(combinedAddress);
        PushManager pushManager = PushManager.get();
        pushManager.connectionBegin(combinedAddress);
        return beginPush;
    }

    public EndPush endPush(RepositoryProvider provider, String repoName, String clientIp,
            String internalIp, String refspec, String objectId, String origRefValue) {
        // get the repo
        Repository repository = getRepository(provider, repoName);
        if (repository != null) {
            try {
                EndPush endPush = new EndPush();
                String combinedAddress = clientIp + "." + internalIp;
                LOGGER.debug("Initiating EndPush for '{}'", combinedAddress);

                final ObjectId oid =  ObjectId.valueOf(
                        objectId != null ? objectId : ObjectId.NULL.toString());
                final ObjectId originalRefValue = ObjectId.valueOf(
                        origRefValue != null ? origRefValue : ObjectId.NULL.toString());

                Optional<Ref> currentRef = repository.command(RefParse.class).setName(refspec).call();
                ObjectId currentRefId = currentRef.isPresent() ? currentRef.get().getObjectId() :
                        ObjectId.NULL;
                if (!currentRefId.isNull() && !currentRefId.equals(originalRefValue)) {
                    // Abort push
                    endPush.setAborted(true);
                    return endPush;
                }
                PushManager pushManager = PushManager.get();
                pushManager.connectionSucceeded(repository, combinedAddress, refspec, oid);
                return endPush;
            } catch (Exception ex) {
                throw new CommandSpecException(ex.getMessage(), HttpStatus.BAD_REQUEST, ex);
            }
        }
        return null;
    }
}
