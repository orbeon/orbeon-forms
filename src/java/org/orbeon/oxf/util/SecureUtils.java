/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.orbeon.oxf.common.OXFException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;

public class SecureUtils {

    private static final byte[] salt = {
        (byte) -26, (byte) 101, (byte) -106, (byte) 2,
        (byte) 61, (byte) -80, (byte) -40, (byte) -8
    };
    private static final int count = 20;

    /**
     * General purpos parameter for algorithm
     */
    private static final PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, count);
    private static final String CIPHER_TYPE = "PBEWithMD5AndDES";

    private static Map passwordToEncryptionCipher = new HashMap();
    private static Map passwordToDecryptionCipher = new HashMap();

    private static SecretKey getSecretKey(String password) {
        try {
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray());
            SecretKeyFactory keyFac = SecretKeyFactory.getInstance(CIPHER_TYPE);
            return keyFac.generateSecret(pbeKeySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (InvalidKeySpecException e) {
            throw new OXFException(e);
        }
    }

    public static Cipher getEncryptingCipher(String password) {
        try {
            synchronized(passwordToEncryptionCipher) {
                Cipher cipher = (Cipher) passwordToEncryptionCipher.get(password);
                if (cipher == null) {
                    cipher = Cipher.getInstance(CIPHER_TYPE);
                    cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password), pbeParamSpec);
                    passwordToEncryptionCipher.put(password, cipher);
                }
                return cipher;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (NoSuchPaddingException e) {
            throw new OXFException(e);
        } catch (InvalidKeyException e) {
            throw new OXFException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new OXFException(e);
        }
    }

    public static Cipher getDecryptingCipher(String password) {
        try {
            synchronized(passwordToDecryptionCipher) {
                Cipher cipher = (Cipher) passwordToDecryptionCipher.get(password);
                if (cipher == null) {
                    cipher = Cipher.getInstance(CIPHER_TYPE);
                    cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password), pbeParamSpec);
                    passwordToDecryptionCipher.put(password, cipher);
                }
                return cipher;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (NoSuchPaddingException e) {
            throw new OXFException(e);
        } catch (InvalidKeyException e) {
            throw new OXFException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new OXFException(e);
        }
    }
}
