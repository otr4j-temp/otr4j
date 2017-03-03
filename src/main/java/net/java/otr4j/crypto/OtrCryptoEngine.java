/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.otr4j.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHPrivateKeySpec;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

import net.java.otr4j.io.SerializationConstants;
import net.java.otr4j.io.SerializationUtils;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.DHKeyPairGenerator;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.DHKeyGenerationParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.crypto.params.DHPrivateKeyParameters;
import org.bouncycastle.crypto.params.DHPublicKeyParameters;
import org.bouncycastle.crypto.params.DSAParameters;
import org.bouncycastle.crypto.params.DSAPrivateKeyParameters;
import org.bouncycastle.crypto.params.DSAPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.signers.DSASigner;
import org.bouncycastle.util.BigIntegers;

/**
 * Utility for cryptographic functions.
 *
 * @author George Politis
 * @author Danny van Heumen
 */
// TODO Investigate what we can reuse of KeyFactories, KeyAgreement instances, MessageDigest instances, Hmac instances.
public final class OtrCryptoEngine {

    private static final String ALGORITHM_DSA = "DSA";
    private static final String KA_DH = "DH";
    private static final String KF_DH = "DH";
    private static final String MD_SHA1 = "SHA-1";
    private static final String MD_SHA256 = "SHA-256";
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String HMAC_SHA256 = "HmacSHA256";

    static {
        // Test initialization of all required cryptographic types that need to
        // be created through their respective factories. This test can function
        // as an early indicator in case support for required types is missing.
        try {
            KeyAgreement.getInstance(KA_DH);
            KeyFactory.getInstance(KF_DH);
            Mac.getInstance(HMAC_SHA256);
            Mac.getInstance(HMAC_SHA1);
            MessageDigest.getInstance(MD_SHA256);
            MessageDigest.getInstance(MD_SHA1);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed initialization test of required cryptographic types. otr4j will not function correctly.", ex);
        }
    }

    private static final String MODULUS_TEXT = "00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF";
    public static final BigInteger MODULUS = new BigInteger(MODULUS_TEXT, 16);
    private static final BigInteger BIGINTEGER_TWO = BigInteger.valueOf(2);
    public static final BigInteger MODULUS_MINUS_TWO = MODULUS.subtract(BIGINTEGER_TWO);
    public static final BigInteger GENERATOR = new BigInteger("2", 10);

    public static final int AES_KEY_BYTE_LENGTH = 16;
    public static final int SHA256_HMAC_KEY_BYTE_LENGTH = 32;
    private static final int DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH = 320;
    private static final byte[] ZERO_CTR = new byte[] {
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00
    };

    private OtrCryptoEngine() {
        // this class is never instantiated, it only has static methods
    }

    @Nonnull
    public static KeyPair generateDHKeyPair(@Nonnull final SecureRandom secureRandom) {

        // Generate a AsymmetricCipherKeyPair using BC.
        final DHParameters dhParams = new DHParameters(MODULUS, GENERATOR, null,
                DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH);
        final DHKeyGenerationParameters params = new DHKeyGenerationParameters(
                secureRandom, dhParams);
        final DHKeyPairGenerator kpGen = new DHKeyPairGenerator();

        kpGen.init(params);
        final AsymmetricCipherKeyPair pair = kpGen.generateKeyPair();
        final DHPublicKeyParameters pub = convertToPublicKeyParams(pair.getPublic());
        final DHPrivateKeyParameters priv = convertToPrivateKeyParams(pair.getPrivate());

        final KeyFactory keyFac;
        try {
            keyFac = KeyFactory.getInstance(KF_DH);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("DH key factory unavailable.", ex);
        }

        final DHPublicKeySpec pubKeySpecs = new DHPublicKeySpec(pub.getY(),
                MODULUS, GENERATOR);
        final DHPublicKey pubKey;
        try {
            pubKey = (DHPublicKey) keyFac
                    .generatePublic(pubKeySpecs);
        } catch (final InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to generate DH public key.", ex);
        }

        final DHParameters dhParameters = priv.getParameters();
        final DHPrivateKeySpec privKeySpecs = new DHPrivateKeySpec(priv.getX(),
                dhParameters.getP(), dhParameters.getG());
        final DHPrivateKey privKey;
        try {
            privKey = (DHPrivateKey) keyFac
                    .generatePrivate(privKeySpecs);
        } catch (final InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to generate DH private key.", ex);
        }

        return new KeyPair(pubKey, privKey);
    }

    @Nonnull
    public static DHPublicKey getDHPublicKey(@Nonnull final BigInteger mpi) throws OtrCryptoException {
        final DHPublicKeySpec pubKeySpecs = new DHPublicKeySpec(mpi, MODULUS, GENERATOR);
        try {
            final KeyFactory keyFac = KeyFactory.getInstance(KF_DH);
            return (DHPublicKey) keyFac.generatePublic(pubKeySpecs);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to instantiate D-H key factory.", ex);
        } catch (final InvalidKeySpecException ex) {
            throw new OtrCryptoException("Invalid D-H public key spec.", ex);
        }
    }

    @Nonnull
    public static byte[] sha256Hmac(@Nonnull final byte[] b, @Nonnull final byte[] key) throws OtrCryptoException {
        return sha256Hmac(b, key, 0);
    }

    @Nonnull
    public static byte[] sha256Hmac(@Nonnull final byte[] b, @Nonnull final byte[] key, final int length) throws OtrCryptoException {

        final SecretKeySpec keyspec = new SecretKeySpec(key, HMAC_SHA256);
        final Mac mac;
        try {
            mac = Mac.getInstance(HMAC_SHA256);
            mac.init(keyspec);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to initialize MAC based on SHA-256.", e);
        } catch (final InvalidKeyException e) {
            throw new OtrCryptoException("Invalid key, results in invalid keyspec.", e);
        }

        final byte[] macBytes = mac.doFinal(b);

        if (length > 0) {
            final byte[] bytes = new byte[length];
            final ByteBuffer buff = ByteBuffer.wrap(macBytes);
            buff.get(bytes);
            return bytes;
        } else {
            return macBytes;
        }
    }

    @Nonnull
    public static byte[] sha1Hmac(@Nonnull final byte[] b, @Nonnull final byte[] key, final int length)
            throws OtrCryptoException {
        final byte[] macBytes;
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(key, HMAC_SHA1));
            macBytes = mac.doFinal(b);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unsupported HMAC function specified.", ex);
        } catch (final InvalidKeyException ex) {
            throw new OtrCryptoException("Invalid key, results in invalid keyspec.", ex);
        }

        if (length > 0) {
            final byte[] bytes = new byte[length];
            ByteBuffer.wrap(macBytes).get(bytes);
            return bytes;
        } else {
            return macBytes;
        }
    }

    @Nonnull
    public static byte[] sha256Hmac160(@Nonnull final byte[] b, @Nonnull final byte[] key) throws OtrCryptoException {
        return sha256Hmac(b, key, SerializationConstants.TYPE_LEN_MAC);
    }

    @Nonnull
    public static byte[] sha256Hash(@Nonnull final byte[] first, final byte[]... next) {
        final MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance(MD_SHA256);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to acquire SHA-256 message digest.", e);
        }
        sha256.update(first);
        for (final byte[] b : next) {
            sha256.update(b, 0, b.length);
        }
        return sha256.digest();
    }

    @Nonnull
    public static byte[] sha1Hash(@Nonnull final byte[] first, final byte[]... next) {
        final MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance(MD_SHA1);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to acquire SHA1 message digest.", e);
        }
        sha1.update(first, 0, first.length);
        for (final byte[] b : next) {
            sha1.update(b, 0, b.length);
        }
        return sha1.digest();
    }

    @Nonnull
    public static byte[] aesDecrypt(@Nonnull final byte[] key, @Nullable byte[] ctr, @Nonnull final byte[] b)
            throws OtrCryptoException {

        final AESEngine aesDec = new AESEngine();
        final SICBlockCipher sicAesDec = new SICBlockCipher(aesDec);
        final BufferedBlockCipher bufSicAesDec = new BufferedBlockCipher(sicAesDec);

        // Create initial counter value 0.
        if (ctr == null) {
            ctr = ZERO_CTR;
        }
        bufSicAesDec.init(false, new ParametersWithIV(new KeyParameter(key),
                ctr));
        final byte[] aesOutLwDec = new byte[b.length];
        final int done = bufSicAesDec.processBytes(b, 0, b.length, aesOutLwDec, 0);
        try {
            bufSicAesDec.doFinal(aesOutLwDec, done);
        } catch (final InvalidCipherTextException ex) {
            throw new OtrCryptoException(ex);
        }

        return aesOutLwDec;
    }

    @Nonnull
    public static byte[] aesEncrypt(@Nonnull final byte[] key, @Nullable byte[] ctr, @Nonnull final byte[] b)
            throws OtrCryptoException {

        final AESEngine aesEnc = new AESEngine();
        final SICBlockCipher sicAesEnc = new SICBlockCipher(aesEnc);
        final BufferedBlockCipher bufSicAesEnc = new BufferedBlockCipher(sicAesEnc);

        // Create initial counter value 0.
        if (ctr == null) {
            ctr = ZERO_CTR;
        }
        bufSicAesEnc.init(true,
                new ParametersWithIV(new KeyParameter(key), ctr));
        final byte[] aesOutLwEnc = new byte[b.length];
        final int done = bufSicAesEnc.processBytes(b, 0, b.length, aesOutLwEnc, 0);
        try {
            bufSicAesEnc.doFinal(aesOutLwEnc, done);
        } catch (final InvalidCipherTextException ex) {
            throw new OtrCryptoException(ex);
        }
        return aesOutLwEnc;
    }

    @Nonnull
    public static SharedSecret generateSecret(@Nonnull final PrivateKey privKey,
            @Nonnull final PublicKey pubKey) throws OtrCryptoException {
        verify((DHPublicKey) pubKey);
        try {
            final KeyAgreement ka = KeyAgreement.getInstance(KA_DH);
            ka.init(privKey);
            ka.doPhase(pubKey, true);
            return new SharedSecret(ka.generateSecret());
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("DH key factory not supported.", ex);
        } catch (final InvalidKeyException ex) {
            throw new OtrCryptoException(ex);
        }
    }

    @Nonnull
    public static byte[] sign(@Nonnull final byte[] b, @Nonnull final PrivateKey privatekey) {

        if (!(privatekey instanceof DSAPrivateKey)) {
            throw new IllegalArgumentException("Private key instance must be of type DSAPrivateKey.");
        }

        final DSAParams dsaParams = ((DSAPrivateKey) privatekey).getParams();
        final DSAParameters bcDSAParameters = new DSAParameters(dsaParams.getP(),
                dsaParams.getQ(), dsaParams.getG());

        final DSAPrivateKey dsaPrivateKey = (DSAPrivateKey) privatekey;
        final DSAPrivateKeyParameters bcDSAPrivateKeyParms = new DSAPrivateKeyParameters(
                dsaPrivateKey.getX(), bcDSAParameters);

        final DSASigner dsaSigner = new DSASigner();
        dsaSigner.init(true, bcDSAPrivateKeyParms);

        final BigInteger q = dsaParams.getQ();

        // Ian: Note that if you can get the standard DSA implementation you're
        // using to not hash its input, you should be able to pass it ((256-bit
        // value) mod q), (rather than truncating the 256-bit value) and all
        // should be well.
        // ref: Interop problems with libotr - DSA signature
        final BigInteger bmpi = new BigInteger(1, b);
        final BigInteger[] rs = dsaSigner.generateSignature(BigIntegers
                .asUnsignedByteArray(bmpi.mod(q)));

        final int siglen = q.bitLength() / 4;
        final int rslen = siglen / 2;
        final byte[] rb = BigIntegers.asUnsignedByteArray(rs[0]);
        final byte[] sb = BigIntegers.asUnsignedByteArray(rs[1]);

        // Create the final signature array, padded with zeros if necessary.
        final byte[] sig = new byte[siglen];
        System.arraycopy(rb, 0, sig, rslen - rb.length, rb.length);
        System.arraycopy(sb, 0, sig, sig.length - sb.length, sb.length);
        return sig;
    }

    /**
     * Verify DSA signature against expectation using provided DSA public key
     * instance..
     *
     * @param b data expected to be signed.
     * @param pubKey Public key. Provided public key must be an instance of
     * DSAPublicKey.
     * @param rs Components R and S.
     * @throws OtrCryptoException Thrown in case of failed verification.
     */
    public static void verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final byte[] rs)
            throws OtrCryptoException {

        if (!(pubKey instanceof DSAPublicKey)) {
            throw new IllegalArgumentException("Public key instance must be of type DSAPublicKey.");
        }

        final DSAParams dsaParams = ((DSAPublicKey) pubKey).getParams();
        final int qlen = dsaParams.getQ().bitLength() / 8;
        final ByteBuffer buff = ByteBuffer.wrap(rs);
        final byte[] r = new byte[qlen];
        buff.get(r);
        final byte[] s = new byte[qlen];
        buff.get(s);
        verify(b, pubKey, r, s);
    }

    private static void verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final byte[] r, @Nonnull final byte[] s)
            throws OtrCryptoException {
        verify(b, pubKey, new BigInteger(1, r), new BigInteger(1, s));
    }

    private static void verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final BigInteger r,
            @Nonnull final BigInteger s) throws OtrCryptoException {

        if (!(pubKey instanceof DSAPublicKey)) {
            throw new IllegalArgumentException("Public key instance must be of type DSAPublicKey.");
        }

        final DSAParams dsaParams = ((DSAPublicKey) pubKey).getParams();

        final BigInteger q = dsaParams.getQ();
        final DSAParameters bcDSAParams = new DSAParameters(dsaParams.getP(), q,
                dsaParams.getG());

        final DSAPublicKey dsaPrivateKey = (DSAPublicKey) pubKey;
        final DSAPublicKeyParameters dsaPrivParms = new DSAPublicKeyParameters(
                dsaPrivateKey.getY(), bcDSAParams);

        // Ian: Note that if you can get the standard DSA implementation you're
        // using to not hash its input, you should be able to pass it ((256-bit
        // value) mod q), (rather than truncating the 256-bit value) and all
        // should be well.
        // ref: Interop problems with libotr - DSA signature
        final DSASigner dsaSigner = new DSASigner();
        dsaSigner.init(false, dsaPrivParms);

        final BigInteger bmpi = new BigInteger(1, b);
        if (!dsaSigner.verifySignature(BigIntegers.asUnsignedByteArray(bmpi.mod(q)), r, s)) {
            throw new OtrCryptoException("DSA signature verification failed.");
        }
    }

    @Nonnull
    public static String getFingerprint(@Nonnull final PublicKey pubKey) {
        final byte[] b = getFingerprintRaw(pubKey);
        return SerializationUtils.byteArrayToHexString(b);
    }

    @Nonnull
    public static byte[] getFingerprintRaw(@Nonnull final PublicKey pubKey) {
        final byte[] bRemotePubKey = SerializationUtils.writePublicKey(pubKey);
        if (pubKey.getAlgorithm().equals(ALGORITHM_DSA)) {
            byte[] trimmed = new byte[bRemotePubKey.length - 2];
            System.arraycopy(bRemotePubKey, 2, trimmed, 0, trimmed.length);
            return OtrCryptoEngine.sha1Hash(trimmed);
        } else {
            return OtrCryptoEngine.sha1Hash(bRemotePubKey);
        }
    }

    @Nonnull
    private static DHPublicKeyParameters convertToPublicKeyParams(@Nonnull final AsymmetricKeyParameter params) {
        if (!(params instanceof DHPublicKeyParameters)) {
            throw new IllegalArgumentException("Expected to acquire DHPublicKeyParameters instance, but it isn't. (" + params.getClass().getCanonicalName() + ")");
        }
        return (DHPublicKeyParameters) params;
    }

    @Nonnull
    private static DHPrivateKeyParameters convertToPrivateKeyParams(@Nonnull final AsymmetricKeyParameter params) {
        if (!(params instanceof DHPrivateKeyParameters)) {
            throw new IllegalArgumentException("Expected to acquire DHPrivateKeyParameters instance, but it isn't. (" + params.getClass().getCanonicalName() + ")");
        }
        return (DHPrivateKeyParameters) params;
    }

    /**
     * Fill provided byte-array with random data from provided
     * {@link SecureRandom} instance. This is a convenience function that can be
     * used in-line for field or variable instantiation.
     *
     * @param random a SecureRandom instance
     * @param dest The destination byte-array to be fully filled with random
     * data.
     * @return Returns 'dest' filled with random data.
     */
    @Nonnull
    public static byte[] random(@Nonnull final SecureRandom random, @Nonnull final byte[] dest) {
        random.nextBytes(dest);
        return dest;
    }

    /**
     * Verify that provided DH public key is a valid key.
     *
     * @param dhPublicKey DH public key
     * @return Returns DH public key instance if DH public key is valid.
     * @throws OtrCryptoException Throws exception in case of illegal D-H key
     * value.
     */
    @Nonnull
    public static DHPublicKey verify(@Nonnull final DHPublicKey dhPublicKey) throws OtrCryptoException {
        // Verifies that Alice's gy is a legal value (2 <= gy <= modulus-2)
        if (dhPublicKey.getY().compareTo(OtrCryptoEngine.MODULUS_MINUS_TWO) > 0) {
            throw new OtrCryptoException("Illegal D-H Public Key value.");
        }
        if (dhPublicKey.getY().compareTo(OtrCryptoEngine.BIGINTEGER_TWO) < 0) {
            throw new OtrCryptoException("Illegal D-H Public Key value.");
        }
        return dhPublicKey;
    }

    /**
     * Equality check for byte arrays that throws an exception in case of
     * failure. This version enables us to put equality checks in line with
     * other code and be sure that we immediately "exit" upon failing a check,
     * hence we cannot forget to handle the verification result.
     *
     * @param a byte[] a
     * @param b byte[] b
     * @param message The exception message in case of arrays are not equal.
     * @throws OtrCryptoException Throws exception in case of inequality.
     */
    public static void checkEquals(final byte[] a, final byte[] b, final String message) throws OtrCryptoException {
        if (!Arrays.equals(a, b)) {
            throw new OtrCryptoException(message);
        }
    }

    /**
     * Create SHA-256 based message digest instance.
     *
     * @return Returns instance of SHA-256 message digest.
     */
    @Nonnull
    public static MessageDigest createSHA256MessageDigest() {
        try {
            return MessageDigest.getInstance(MD_SHA256);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to acquire SHA-256 message digest.", ex);
        }
    }

    /**
     * (Re)Create DSA public key based on provided input parameters.
     *
     * @param y y
     * @param p p
     * @param q q
     * @param g g
     * @return Returns DSA public key.
     * @throws OtrCryptoException Throws OtrCryptoException in case of failure to create DSA public key.
     */
    @Nonnull
    public static PublicKey createDSAPublicKey(@Nonnull final BigInteger y, @Nonnull final BigInteger p,
                                               @Nonnull final BigInteger q, @Nonnull final BigInteger g)
            throws OtrCryptoException {
        final DSAPublicKeySpec keySpec = new DSAPublicKeySpec(y, p, q, g);
        final KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance("DSA");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize DSA key factory.", e);
        }
        try {
            return keyFactory.generatePublic(keySpec);
        } catch (final InvalidKeySpecException e) {
            throw new OtrCryptoException("Read invalid public key from input stream.", e);
        }
    }
}
