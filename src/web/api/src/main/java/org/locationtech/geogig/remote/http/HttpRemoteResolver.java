/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Optional;

/**
 * {@link RemoteResolver} implementation that works against the HTTP web API
 */
public class HttpRemoteResolver implements RemoteResolver {

    public @Override Optional<IRemoteRepo> resolve(Remote remote, Hints remoteHints) {

        IRemoteRepo remoteRepo = null;

        try {
            String fetchURL = remote.getFetchURL();
            URI fetchURI = URI.create(fetchURL);
            final String protocol = fetchURI.getScheme();

            if ("http".equals(protocol) || "https".equals(protocol)) {
                final String username = remote.getUserName();
                final String password = remote.getPassword();
                if (username != null && password != null) {
                    Authenticator.setDefault(new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username,
                                    decryptPassword(password).toCharArray());
                        }
                    });
                } else {
                    Authenticator.setDefault(null);
                }
                if (remote.getMapped()) {
                    remoteRepo = new HttpMappedRemoteRepo(remote, fetchURI.toURL());
                } else {
                    remoteRepo = new HttpRemoteRepo(remote, fetchURI.toURL());
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Optional.fromNullable(remoteRepo);
    }

    private static final char[] PASSWORD = "jd4nvds832lsn4apq".toCharArray();

    private static final byte[] SALT = { (byte) 0xa2, (byte) 0x18, (byte) 0xd6, (byte) 0xd6,
            (byte) 0xf1, (byte) 0x2e, (byte) 0x0a, (byte) 0x7b, };

    /**
     * Encrypts a text password so that it can be safely written to a database.
     * 
     * @param password the password to encrypt
     * @return the encrypted password
     */
    public static String encryptPassword(String password) {
        try {
            String keyfacname = "PBEWithMD5AndDES";
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(keyfacname);
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(PASSWORD));
            Cipher pbeCipher = Cipher.getInstance(keyfacname);
            pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
            return Base64.getEncoder()
                    .encodeToString(pbeCipher.doFinal(password.getBytes("UTF-8")));
        } catch (Exception e) {
            return password;
        }
    }

    /**
     * Decrypts an encrypted password.
     * 
     * @param password the encrypted password
     * @return the decrypted password
     */
    public static String decryptPassword(String password) {
        try {
            final String keyfacname = "PBEWithMD5AndDES";
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(keyfacname);
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(PASSWORD));
            Cipher pbeCipher = Cipher.getInstance(keyfacname);
            pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
            return new String(pbeCipher.doFinal(Base64.getDecoder().decode(password)));
        } catch (Exception e) {
            return password;
        }
    }

}
