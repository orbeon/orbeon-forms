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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SecureUtils {

    private static final byte[] salt = {
        (byte) -26, (byte) 101, (byte) -106, (byte) 2,
        (byte) 61, (byte) -80, (byte) -40, (byte) -8
    };
    private static final int count = 20;
    private static Random random = new Random();

    /**
     * General purpos parameter for algorithm
     */
    private static final PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, count);
    private static final String CIPHER_TYPE = "PBEWithMD5AndDES";

    private static Map passwordToEncryptionCipher = new HashMap();
    private static Map passwordToDecryptionCipher = new HashMap();

    public static Cipher getEncryptingCipher(String password, boolean cacheCipher) {
        try {
            Cipher cipher;
            if (cacheCipher) {
                synchronized(passwordToEncryptionCipher) {
                    cipher = (Cipher) passwordToEncryptionCipher.get(password);
                    if (cipher == null) {
                        cipher = Cipher.getInstance(CIPHER_TYPE);
                        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password), pbeParamSpec);
                        passwordToEncryptionCipher.put(password, cipher);
                    }
                }
            } else {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password), pbeParamSpec);
            }
            return cipher;
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

    public static Cipher getDecryptingCipher(String password, boolean cacheCipher) {
        try {
            Cipher cipher;
            if (cacheCipher) {
                synchronized(passwordToDecryptionCipher) {
                    cipher = (Cipher) passwordToDecryptionCipher.get(password);
                    if (cipher == null) {
                        cipher = Cipher.getInstance(CIPHER_TYPE);
                        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password), pbeParamSpec);
                        passwordToDecryptionCipher.put(password, cipher);
                    }
                }
            } else {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password), pbeParamSpec);
            }
            return cipher;
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

    public static String generateRandomPassword() {
        // There is obviously room for improvement in our "password generation algorithm"
        return Long.toString(random.nextLong());
    }

    public static String encrypt(PipelineContext pipelineContext, String password, String text) {
        try {
            Cache cache = ObjectCache.instance();
            Long validity = new Long(0);
            InternalCacheKey key = new InternalCacheKey("Encryption cipher", password);
            Cipher cipher = (Cipher) cache.findValid(pipelineContext, key, validity);
            if (cipher == null) {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password), pbeParamSpec);
                cache.add(pipelineContext, key, validity, cipher);
            }
            synchronized(cipher) {
                return Base64.encode(cipher.doFinal(text.toString().getBytes()));
            }
        } catch (IllegalBlockSizeException e) {
            throw new OXFException(e);
        } catch (BadPaddingException e) {
            throw new OXFException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new OXFException(e);
        } catch (InvalidKeyException e) {
            throw new OXFException(e);
        } catch (NoSuchPaddingException e) {
            throw new OXFException(e);
        }
    }

    public static String decrypt(PipelineContext pipelineContext, String password, String text) {
        try {
            Cache cache = ObjectCache.instance();
            Long validity = new Long(0);
            InternalCacheKey key = new InternalCacheKey("Decryption cipher", password);
            Cipher cipher = (Cipher) cache.findValid(pipelineContext, key, validity);
            if (cipher == null) {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password), pbeParamSpec);
                cache.add(pipelineContext, key, validity, cipher);
            }
            synchronized(cipher) {
                return new String(cipher.doFinal(Base64.decode(text)));
            }
        } catch (IllegalBlockSizeException e) {
            throw new OXFException(e);
        } catch (BadPaddingException e) {
            throw new OXFException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new OXFException(e);
        } catch (InvalidKeyException e) {
            throw new OXFException(e);
        } catch (NoSuchPaddingException e) {
            throw new OXFException(e);
        }
    }

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
}
