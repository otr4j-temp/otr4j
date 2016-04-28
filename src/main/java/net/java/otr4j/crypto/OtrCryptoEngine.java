/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.otr4j.crypto;

import java.io.IOException;
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
import java.security.spec.InvalidKeySpecException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.crypto.KeyAgreement;
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
import org.bouncycastle.crypto.engines.AESFastEngine;
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
 * @author George Politis
 */
public class OtrCryptoEngine {

    private static final String ALGORITHM_DSA = "DSA";
    private static final String KA_DH = "DH";
    private static final String KF_DH = "DH";
    private static final String MD_SHA1 = "SHA-1";
    private static final String MD_SHA256 = "SHA-256";
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static final String MODULUS_TEXT = "00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF";
    public static final BigInteger MODULUS = new BigInteger(MODULUS_TEXT, 16);
    public static final BigInteger BIGINTEGER_TWO = BigInteger.valueOf(2);
    public static final BigInteger MODULUS_MINUS_TWO = MODULUS.subtract(BIGINTEGER_TWO);
    public static final BigInteger GENERATOR = new BigInteger("2", 10);

    public static final int AES_KEY_BYTE_LENGTH = 16;
    public static final int SHA256_HMAC_KEY_BYTE_LENGTH = 32;
    public static final int DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH = 320;
    private static final byte[] ZERO_CTR = new byte[] {
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00
    };

    public static final int DSA_PUB_TYPE = 0;

    private OtrCryptoEngine() {
        // this class is never instantiated, it only has static methods
    }

    @Nonnull
    public static KeyPair generateDHKeyPair(@Nonnull final SecureRandom secureRandom) throws OtrCryptoException {

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

        try {
            final KeyFactory keyFac = KeyFactory.getInstance(KF_DH);

            final DHPublicKeySpec pubKeySpecs = new DHPublicKeySpec(pub.getY(),
                    MODULUS, GENERATOR);
            final DHPublicKey pubKey = (DHPublicKey) keyFac
                    .generatePublic(pubKeySpecs);

            final DHParameters dhParameters = priv.getParameters();
            final DHPrivateKeySpec privKeySpecs = new DHPrivateKeySpec(priv.getX(),
                    dhParameters.getP(), dhParameters.getG());
            final DHPrivateKey privKey = (DHPrivateKey) keyFac
                    .generatePrivate(privKeySpecs);

            return new KeyPair(pubKey, privKey);
        } catch (NoSuchAlgorithmException ex) {
            throw new OtrCryptoException(ex);
        } catch (InvalidKeySpecException ex) {
            throw new OtrCryptoException(ex);
        }
    }

    @Nonnull
    public static DHPublicKey getDHPublicKey(@Nonnull final byte[] mpiBytes)
            throws OtrCryptoException {
        return getDHPublicKey(new BigInteger(mpiBytes));
    }

    @Nonnull
    public static DHPublicKey getDHPublicKey(@Nonnull final BigInteger mpi) throws OtrCryptoException {
        final DHPublicKeySpec pubKeySpecs = new DHPublicKeySpec(mpi, MODULUS,
                GENERATOR);
        try {
            final KeyFactory keyFac = KeyFactory.getInstance(KF_DH);
            return (DHPublicKey) keyFac.generatePublic(pubKeySpecs);
        } catch (NoSuchAlgorithmException ex) {
            throw new OtrCryptoException(ex);
        } catch (InvalidKeySpecException ex) {
            throw new OtrCryptoException(ex);
        }
    }

    public static byte[] sha256Hmac(@Nonnull final byte[] b, @Nonnull final byte[] key)
            throws OtrCryptoException {
        return sha256Hmac(b, key, 0);
    }

    @Nonnull
    public static byte[] sha256Hmac(@Nonnull final byte[] b, @Nonnull final byte[] key, final int length)
            throws OtrCryptoException {

        final SecretKeySpec keyspec = new SecretKeySpec(key, HMAC_SHA256);
        final javax.crypto.Mac mac;
        try {
            mac = javax.crypto.Mac.getInstance(HMAC_SHA256);
        } catch (NoSuchAlgorithmException e) {
            throw new OtrCryptoException(e);
        }
        try {
            mac.init(keyspec);
        } catch (InvalidKeyException e) {
            throw new OtrCryptoException(e);
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
            final javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(key, HMAC_SHA1));
            macBytes = mac.doFinal(b);
        } catch (NoSuchAlgorithmException ex) {
            throw new OtrCryptoException(ex);
        } catch (InvalidKeyException ex) {
            throw new OtrCryptoException(ex);
        }

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
    public static byte[] sha256Hmac160(@Nonnull final byte[] b, @Nonnull final byte[] key) throws OtrCryptoException {
        return sha256Hmac(b, key, SerializationConstants.TYPE_LEN_MAC);
    }

    @Nonnull
    public static byte[] sha256Hash(@Nonnull final byte[] b) throws OtrCryptoException {
        try {
            final MessageDigest sha256 = MessageDigest.getInstance(MD_SHA256);
            sha256.update(b, 0, b.length);
            return sha256.digest();
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Nonnull
    public static byte[] sha1Hash(@Nonnull final byte[] b) throws OtrCryptoException {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance(MD_SHA1);
            sha1.update(b, 0, b.length);
            return sha1.digest();
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Nonnull
    public static byte[] aesDecrypt(@Nonnull final byte[] key, @Nullable byte[] ctr, @Nonnull final byte[] b)
            throws OtrCryptoException {

        final AESFastEngine aesDec = new AESFastEngine();
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
        } catch (InvalidCipherTextException ex) {
            throw new OtrCryptoException(ex);
        }

        return aesOutLwDec;
    }

    @Nonnull
    public static byte[] aesEncrypt(@Nonnull final byte[] key, @Nullable byte[] ctr, @Nonnull final byte[] b)
            throws OtrCryptoException {

        final AESFastEngine aesEnc = new AESFastEngine();
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
        } catch (InvalidCipherTextException ex) {
            throw new OtrCryptoException(ex);
        }
        return aesOutLwEnc;
    }

    @Nonnull
    public static BigInteger generateSecret(@Nonnull final PrivateKey privKey, @Nonnull final PublicKey pubKey)
            throws OtrCryptoException {
        try {
            final KeyAgreement ka = KeyAgreement.getInstance(KA_DH);
            ka.init(privKey);
            ka.doPhase(pubKey, true);
            final byte[] sb = ka.generateSecret();
            return new BigInteger(1, sb);
        } catch (NoSuchAlgorithmException ex) {
            throw new OtrCryptoException(ex);
        } catch (InvalidKeyException ex) {
            throw new OtrCryptoException(ex);
        }
    }

    @Nonnull
    public static byte[] sign(@Nonnull final byte[] b, @Nonnull final PrivateKey privatekey)
            throws OtrCryptoException {

        if (!(privatekey instanceof DSAPrivateKey)) {
            throw new IllegalArgumentException();
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
     * Verify ...
     * 
     * @param b
     * @param pubKey Public key. Provided public key must be an instance of DSAPublicKey.
     * @param rs
     * @return
     * @throws OtrCryptoException 
     */
    public static boolean verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final byte[] rs)
            throws OtrCryptoException {

        if (!(pubKey instanceof DSAPublicKey)) {
            throw new IllegalArgumentException();
        }

        final DSAParams dsaParams = ((DSAPublicKey) pubKey).getParams();
        final int qlen = dsaParams.getQ().bitLength() / 8;
        final ByteBuffer buff = ByteBuffer.wrap(rs);
        final byte[] r = new byte[qlen];
        buff.get(r);
        final byte[] s = new byte[qlen];
        buff.get(s);
        return verify(b, pubKey, r, s);
    }

    private static boolean verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final byte[] r, @Nonnull final byte[] s)
            throws OtrCryptoException {
        return verify(b, pubKey, new BigInteger(1, r), new BigInteger(1, s));
    }

    private static boolean verify(@Nonnull final byte[] b, @Nonnull final PublicKey pubKey, @Nonnull final BigInteger r,
            @Nonnull final BigInteger s) throws OtrCryptoException {

        if (!(pubKey instanceof DSAPublicKey)) {
            throw new IllegalArgumentException();
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
        return dsaSigner.verifySignature(BigIntegers
                .asUnsignedByteArray(bmpi.mod(q)), r, s);
    }

    @Nonnull
    public static String getFingerprint(@Nonnull final PublicKey pubKey) throws OtrCryptoException {
        final byte[] b = getFingerprintRaw(pubKey);
        return SerializationUtils.byteArrayToHexString(b);
    }

    @Nonnull
    public static byte[] getFingerprintRaw(@Nonnull final PublicKey pubKey)
            throws OtrCryptoException {
        try {
            final byte[] bRemotePubKey = SerializationUtils.writePublicKey(pubKey);

            final byte[] b;
            if (pubKey.getAlgorithm().equals(ALGORITHM_DSA)) {
                byte[] trimmed = new byte[bRemotePubKey.length - 2];
                System.arraycopy(bRemotePubKey, 2, trimmed, 0, trimmed.length);
                b = OtrCryptoEngine.sha1Hash(trimmed);
            } else {
                b = OtrCryptoEngine.sha1Hash(bRemotePubKey);
            }
            return b;
        } catch (IOException e) {
            throw new OtrCryptoException(e);
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
}
