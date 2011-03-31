/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
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

    private static Map<String, Cipher> passwordToEncryptionCipher = new HashMap<String, Cipher>();
    private static Map<String, Cipher> passwordToDecryptionCipher = new HashMap<String, Cipher>();

    public static Cipher getEncryptingCipher(String password, boolean cacheCipher) {
        try {
            Cipher cipher;
            if (cacheCipher) {
                synchronized(passwordToEncryptionCipher) {
                    cipher = passwordToEncryptionCipher.get(password);
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
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static Cipher getDecryptingCipher(String password, boolean cacheCipher) {
        try {
            Cipher cipher;
            if (cacheCipher) {
                synchronized(passwordToDecryptionCipher) {
                    cipher = passwordToDecryptionCipher.get(password);
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
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static String generateRandomPassword() {
        // There is obviously room for improvement in our "password generation algorithm"
        return Long.toString(random.nextLong());
    }

    /**
     * Encrypt a string of text using the given password. The result is converted to Base64 encoding without line
     * breaks or spaces.
     *
     * @param password          encryption password
     * @param text              string to encrypt
     * @return                  string containing the encoding data as Base64
     */
    public static String encrypt(String password, String text) {
        try {
            return encrypt(password, text.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Encrypt a byte array using the given password. The result is converted to Base64 encoding without line breaks
     * or spaces.
     *
     * @param password          encryption password
     * @param bytes             byte array to encrypt
     * @return                  string containing the encoding data as Base64
     */
    public static String encrypt(String password, byte[] bytes) {
        try {
            // Find cipher in cache
            final Cache cache = ObjectCache.instance();
            final Long validity = (long) 0;
            final InternalCacheKey key = new InternalCacheKey("Encryption cipher", password);
            Cipher cipher = (Cipher) cache.findValid(key, validity);
            if (cipher == null) {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password), pbeParamSpec);
                cache.add(key, validity, cipher);
            }
            // Encode with cipher
            // TODO: should probably use pool of cyphers instead of synchronization, which can cause contention here.
            synchronized(cipher) {
                // Encode without putting line breaks between lines
                return Base64.encode(cipher.doFinal(bytes), false);
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Decrypt a Base64-encoded string using the given password.
     *
     * @param password          encryption password
     * @param text              string to decrypt
     * @return                  string containing the decoded data
     */
    public static String decryptAsString(String password, String text) {
        try {
            return new String(decrypt(password, text), "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Decrypt a Base64-encoded string into a byte array using the given password.
     *
     * @param password          encryption password
     * @param text              string to decrypt
     * @return                  byte array containing the decoded data
     */
    public static byte[] decrypt(String password, String text) {
        try {
            Cache cache = ObjectCache.instance();
            Long validity = (long) 0;
            InternalCacheKey key = new InternalCacheKey("Decryption cipher", password);
                Cipher cipher = (Cipher) cache.findValid(key, validity);
            if (cipher == null) {
                cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password), pbeParamSpec);
                cache.add(key, validity, cipher);
            }
            synchronized(cipher) {
                return cipher.doFinal(Base64.decode(text));
            }
        } catch (Exception e) {
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

    public static String digestString(String data, String algorithm, String encoding) {
        try {
            return digestBytes(data.getBytes("utf-8"), algorithm, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException("Exception computing digest with algorithm: " + algorithm, e);
        }
    }

    public static String digestBytes(byte[] data, String algorithm, String encoding) {
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new OXFException("Exception computing digest with algorithm: " + algorithm, e);
        }
        messageDigest.update(data);
        byte[] digestBytes = messageDigest.digest();

        // Format result
        final String result;
        if ("base64".equals(encoding)) {
            result = Base64.encode(digestBytes);
        } else if ("hex".equals(encoding)) {
            result = byteArrayToHex(digestBytes);
        } else {
            throw new OXFException("Invalid digest encoding (must be one of 'base64' or 'hex'): " + encoding);
        }
        return result;
    }

    public static String hmacString(String key, String data, String algorithm, String encoding) {
    	try {
    		return hmacBytes(key.getBytes("utf-8"), data.getBytes("utf-8"), algorithm, encoding);
    	} catch (UnsupportedEncodingException e) {
    		throw new OXFException("Exception computing digest with algorithm: " + algorithm, e);
    	}
    }
    
    public static String hmacBytes(byte[] key, byte[] data, String algorithm, String encoding) {
    	SecretKey secretKey;
    	Mac mac;
    	try {
    		secretKey = new SecretKeySpec(key, algorithm);
    		mac = Mac.getInstance("Hmac" + algorithm.toUpperCase().replace("-", ""));
    		
    		mac.init(secretKey);
    	} catch (NoSuchAlgorithmException e) {
    		throw new OXFException("Exception computing hmac with algorithm: " + algorithm, e);
    	} catch (InvalidKeyException e) {
    		throw new OXFException("Exception computing hmac with algorithm: " + algorithm, e);
		}
    	byte[] digestBytes = mac.doFinal(data);
    	
    	// Format result
    	final String result;
    	if ("base64".equals(encoding)) {
    		result = Base64.encode(digestBytes);
    	} else if ("hex".equals(encoding)) {
    		result = byteArrayToHex(digestBytes);
    	} else {
    		throw new OXFException("Invalid digest encoding (must be one of 'base64' or 'hex'): " + encoding);
    	}
    	return result.replace("\n", "");
    }
    
    // As of 2009-03-11 XForms 1.1 says digits should be lowercase.
    private static final char[] HEXADECIMAL_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public static String byteArrayToHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(HEXADECIMAL_DIGITS[(bytes[i] >> 4) & 0xf]);
            sb.append(HEXADECIMAL_DIGITS[bytes[i] & 0xf]);
        }
        return sb.toString();
    }
}
