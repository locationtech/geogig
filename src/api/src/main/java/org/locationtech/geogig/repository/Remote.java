/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Internal representation of a GeoGig remote repository.
 * 
 * @since 1.0
 */
public class Remote {
    private String name;

    private String fetchurl;

    private String pushurl;

    private String fetch;

    private String mappedBranch;

    private String username;

    private String password;

    private boolean mapped;

    /**
     * Constructs a new remote with the given parameters.
     * 
     * @param name the name of the remote
     * @param fetchurl the fetch URL of the remote
     * @param pushurl the push URL of the remote
     * @param fetch the fetch string of the remote
     * @param mapped whether or not this remote is mapped
     * @param mappedBranch the branch the remote is mapped to
     * @param username the user name to access the repository
     * @param password the password to access the repository
     */
    public Remote(String name, String fetchurl, String pushurl, String fetch, boolean mapped,
            @Nullable String mappedBranch, @Nullable String username, @Nullable String password) {
        this.name = name;
        this.fetchurl = checkURL(fetchurl);
        this.pushurl = checkURL(pushurl);
        this.fetch = fetch;
        this.mapped = mapped;
        this.mappedBranch = Optional.fromNullable(mappedBranch).or("*");
        this.username = username;
        this.password = password;
    }

    /**
     * Ensure that the provided url is valid and not {@code null}.
     * 
     * @param url the url to check
     * @return the url, if it passed validation
     * @throws IllegalArgumentException
     */
    private String checkURL(String url) {
        Preconditions.checkArgument(url != null, "Invalid remote URL.");

        try {
            new URI(url);
        } catch (URISyntaxException e) {
            // See if it's a valid file URI
            try {
                new URI("file:/" + url);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Invalid remote URL.");
            }
        }
        //
        // // we were just checking to see whether the url was already valid. if not, we'll try to
        // // resolve it as a local file
        //
        // File file = new File(url);
        // try {
        // url = file.toURI().toURL().toString();
        // } catch (MalformedURLException e) {
        // // shouldn't reach here, since the file exists and the path should then be correct
        // return url;
        // }
        return url;
    }

    /**
     * @return the name of the remote
     */
    public String getName() {
        return name;
    }

    /**
     * @return the fetch URL of the remote
     */
    public String getFetchURL() {
        return fetchurl;
    }

    /**
     * @return the push URL of the remote
     */
    public String getPushURL() {
        return pushurl;
    }

    /**
     * @return the fetch string of the remote
     */
    public String getFetch() {
        return fetch;
    }

    /**
     * @return whether or not this remote is mapped
     */
    public boolean getMapped() {
        return mapped;
    }

    /**
     * @return the branch the remote is mapped to
     */
    public String getMappedBranch() {
        return mappedBranch;
    }

    /**
     * @return the user name to access the repository
     */
    public String getUserName() {
        return username;
    }

    /**
     * @return the password to access the repository
     */
    public String getPassword() {
        return password;
    }

    /**
     * Determines if this Remote is the same as the given Remote.
     * 
     * @param o the remote to compare against
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Remote)) {
            return false;
        }
        Remote r = (Remote) o;
        return fetch.equals(r.fetch) && fetchurl.equals(r.fetchurl) && pushurl.equals(r.pushurl)
                && name.equals(r.name) && (mapped == r.mapped)
                && stringsEqual(mappedBranch, r.mappedBranch) && stringsEqual(username, r.username)
                && stringsEqual(password, r.password);
    }

    private boolean stringsEqual(String s1, String s2) {
        return (s1 == null ? s2 == null : s1.equals(s2));
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
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(PASSWORD));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
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
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(PASSWORD));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
            return new String(pbeCipher.doFinal(Base64.getDecoder().decode(password)));
        } catch (Exception e) {
            return password;
        }
    }
}
