/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.crypto;

import net.java.otr4j.crypto.ed448.ECDHKeyPair;
import net.java.otr4j.crypto.ed448.Point;
import net.java.otr4j.crypto.ed448.ValidationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SecureRandom;

import static java.util.Objects.requireNonNull;
import static net.java.otr4j.crypto.DHKeyPair.checkPublicKey;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.BRACE_KEY;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SHARED_SECRET;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SSID;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.THIRD_BRACE_KEY;
import static net.java.otr4j.crypto.OtrCryptoEngine4.hwc;
import static net.java.otr4j.crypto.OtrCryptoEngine4.kdf;
import static net.java.otr4j.crypto.ed448.Ed448.containsPoint;
import static org.bouncycastle.util.Arrays.clear;
import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;

/**
 * The OTRv4 Mixed Shared Secret-mechanism.
 */
public final class MixedSharedSecret implements AutoCloseable {

    private static final int SSID_LENGTH_BYTES = 8;
    private static final int BRACE_KEY_LENGTH_BYTES = 32;
    private static final int K_LENGTH_BYTES = 64;

    /**
     * SecureRandom instance.
     */
    private final SecureRandom random;

    /**
     * Either a hash of the shared DH key: 'KDF_1(0x02 || k_dh, 32)' (every third DH ratchet) or a hash of the previous
     * 'brace_key: KDF_1(0x03 || brace_key, 32)'.
     */
    private final byte[] braceKey = new byte[BRACE_KEY_LENGTH_BYTES];

    /**
     * The Mixed shared secret 'K' is the final shared secret derived from both the brace key and ECDH shared secrets:
     * 'KDF_1(0x04 || K_ecdh || brace_key, 64)'.
     */
    private final byte[] k = new byte[K_LENGTH_BYTES];

    /**
     * Flag used to manage internal state: in use / closed.
     */
    private boolean closed = false;

    /**
     * Our ECDH key pair.
     */
    @Nonnull
    private ECDHKeyPair ecdhKeyPair;

    /**
     * The 3072-bit DH shared secret computed from a DH key exchange, serialized as a big-endian unsigned integer.
     */
    @Nonnull
    private DHKeyPair dhKeyPair;

    /**
     * Other party's ECDH public key.
     */
    @Nonnull
    private Point theirECDHPublicKey;

    /**
     * Other party's DH public key.
     */
    @Nonnull
    private BigInteger theirDHPublicKey;

    /**
     * Shared Secret 4.
     *
     * @param random             the secure random instance
     * @param ourDHKeyPair       our DH key pair
     * @param ourECDHKeyPair     our ECDH key pair
     * @param theirDHPublicKey   their DH public key
     * @param theirECDHPublicKey their ECDH public key
     */
    public MixedSharedSecret(final SecureRandom random, final DHKeyPair ourDHKeyPair, final ECDHKeyPair ourECDHKeyPair,
            final BigInteger theirDHPublicKey, final Point theirECDHPublicKey) {
        this.random = requireNonNull(random);
        this.ecdhKeyPair = requireNonNull(ourECDHKeyPair);
        this.theirECDHPublicKey = requireNonNull(theirECDHPublicKey);
        this.dhKeyPair = requireNonNull(ourDHKeyPair);
        this.theirDHPublicKey = requireNonNull(theirDHPublicKey);
        regenerateK(true);
    }

    /**
     * Close MixedSharedSecret instance by securely clearing used memory that contains sensitive data.
     */
    @Override
    public void close() {
        this.closed = true;
        clear(this.braceKey);
        clear(this.k);
        this.ecdhKeyPair.close();
        this.dhKeyPair.close();
    }

    /**
     * Get ephemeral ECDH public key.
     *
     * @return Returns the public key.
     */
    @Nonnull
    public Point getECDHPublicKey() {
        return this.ecdhKeyPair.getPublicKey();
    }

    /**
     * Get ephemeral DH public key.
     *
     * @return Returns the public key.
     */
    @Nonnull
    public BigInteger getDHPublicKey() {
        return this.dhKeyPair.getPublicKey();
    }

    /**
     * Get "their" (remote) ECDH public key.
     *
     * @return Returns the public key.
     */
    @Nonnull
    public Point getTheirECDHPublicKey() {
        return theirECDHPublicKey;
    }

    /**
     * Get "their" (remote) DH public key.
     *
     * @return Returns the public key.
     */
    @Nonnull
    public BigInteger getTheirDHPublicKey() {
        return theirDHPublicKey;
    }

    /**
     * Get mixed shared secret K.
     *
     * @return Mixed shared secret K.
     */
    @Nonnull
    public byte[] getK() {
        expectNotClosed();
        return this.k.clone();
    }

    /**
     * Generate SSID.
     * <p>
     * NOTE: the only relevant SSID value is the one that is generated from the initial DAKE security parameters.
     * If you call this method at any later time, check if you are sure that you want *this* value or the initial SSID.
     *
     * @return Returns the SSID.
     */
    @Nonnull
    public byte[] generateSSID() {
        expectNotClosed();
        return hwc(SSID, SSID_LENGTH_BYTES, this.k);
    }

    /**
     * Rotate our key pairs in the shared secret.
     *
     * @param regenerateDHKeyPair Indicates whether we need to regenerate the DH key pair as well.
     */
    public void rotateOurKeys(final boolean regenerateDHKeyPair) {
        expectNotClosed();
        this.ecdhKeyPair = ECDHKeyPair.generate(this.random);
        if (regenerateDHKeyPair) {
            this.dhKeyPair = DHKeyPair.generate(this.random);
        }
        regenerateK(regenerateDHKeyPair);
    }

    /**
     * Rotate their public keys in the shared secret.
     *
     * @param performDHRatchet   Indicates whether we need to perform a DH ratchet.
     * @param theirECDHPublicKey Their ECDH public key.
     * @param theirDHPublicKey   Their DH public key. (Optional)
     * @throws OtrCryptoException In case of failure to rotate the public keys.
     */
    public void rotateTheirKeys(final boolean performDHRatchet, final Point theirECDHPublicKey,
            @Nullable final BigInteger theirDHPublicKey) throws OtrCryptoException {
        expectNotClosed();
        if (!containsPoint(requireNonNull(theirECDHPublicKey))) {
            throw new OtrCryptoException("ECDH public key failed verification.");
        }
        if (this.ecdhKeyPair.getPublicKey().constantTimeEquals(theirECDHPublicKey)
                || this.theirECDHPublicKey.constantTimeEquals(theirECDHPublicKey)) {
            throw new OtrCryptoException("A new, different ECDH public key is expected for initializing the new ratchet.");
        }
        if (this.dhKeyPair.getPublicKey().equals(theirDHPublicKey) || this.theirDHPublicKey.equals(theirDHPublicKey)) {
            throw new OtrCryptoException("A new, different DH public key is expected for initializing the new ratchet.");
        }
        this.theirECDHPublicKey = theirECDHPublicKey;
        if (performDHRatchet) {
            final BigInteger existingDhPublicKey = requireNonNull(theirDHPublicKey);
            if (!checkPublicKey(existingDhPublicKey)) {
                throw new OtrCryptoException("DH public key failed verification.");
            }
            this.theirDHPublicKey = existingDhPublicKey;
        }
        regenerateK(performDHRatchet);
        this.ecdhKeyPair.close();
        if (performDHRatchet) {
            // Only clear the DH keypair after a DH ratchet has been performed thus a new brace key is available.
            this.dhKeyPair.close();
        }
    }

    @SuppressWarnings("PMD.LocalVariableNamingConventions")
    private void regenerateK(final boolean performDHRatchet) {
        final byte[] k_ecdh;
        try (Point sharedSecret = this.ecdhKeyPair.generateSharedSecret(this.theirECDHPublicKey)) {
            k_ecdh = sharedSecret.encode();
        } catch (final ValidationException e) {
            throw new IllegalStateException("BUG: ECDH public keys should have been verified. No unexpected failures should happen at this point.", e);
        }
        if (performDHRatchet) {
            final byte[] k_dh = asUnsignedByteArray(this.dhKeyPair.generateSharedSecret(this.theirDHPublicKey));
            kdf(this.braceKey, 0, THIRD_BRACE_KEY, BRACE_KEY_LENGTH_BYTES, k_dh);
            clear(k_dh);
        } else {
            kdf(this.braceKey, 0, BRACE_KEY, BRACE_KEY_LENGTH_BYTES, this.braceKey);
        }
        kdf(this.k, 0, SHARED_SECRET, K_LENGTH_BYTES, k_ecdh, this.braceKey);
        clear(k_ecdh);
    }

    private void expectNotClosed() {
        if (this.closed) {
            throw new IllegalStateException("Shared secret is already closed/disposed of.");
        }
    }
}
