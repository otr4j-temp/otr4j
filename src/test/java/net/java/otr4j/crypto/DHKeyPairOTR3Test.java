/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.crypto;

import org.junit.Test;

import javax.crypto.interfaces.DHPublicKey;
import java.security.SecureRandom;

import static java.math.BigInteger.ONE;
import static net.java.otr4j.crypto.DHKeyPairOTR3.MODULUS;
import static net.java.otr4j.crypto.DHKeyPairOTR3.fromBigInteger;
import static net.java.otr4j.crypto.DHKeyPairOTR3.generateDHKeyPair;
import static net.java.otr4j.crypto.DHKeyPairOTR3.verifyDHPublicKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("ConstantConditions")
public final class DHKeyPairOTR3Test {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test(expected = NullPointerException.class)
    public void testGenerateNullSharedSecret() throws OtrCryptoException {
        final DHKeyPairOTR3 keypair = generateDHKeyPair(RANDOM);
        keypair.generateSharedSecret(null);
    }

    @Test
    public void testGeneratedSharedSecretEqual() throws OtrCryptoException {
        final DHKeyPairOTR3 aliceDHKeyPair = generateDHKeyPair(RANDOM);
        final DHKeyPairOTR3 bobDHKeyPair = generateDHKeyPair(RANDOM);

        assertEquals(aliceDHKeyPair.generateSharedSecret(bobDHKeyPair.getPublic()),
                bobDHKeyPair.generateSharedSecret(aliceDHKeyPair.getPublic()));
    }

    @Test(expected = NullPointerException.class)
    public void testGenerateDHKeyPairNullRandom() {
        generateDHKeyPair(null);
    }

    @Test
    public void testGenerateDHKeyPair() throws OtrCryptoException {
        final DHKeyPairOTR3 keypair = generateDHKeyPair(RANDOM);
        assertNotNull(keypair);
        verifyDHPublicKey(keypair.getPublic());
    }

    @Test(expected = NullPointerException.class)
    public void testConvertFromBigIntegerNull() throws OtrCryptoException {
        fromBigInteger(null);
    }

    @Test
    public void testConvertPublicKeyFromBigInteger() throws OtrCryptoException {
        final DHKeyPairOTR3 keypair = generateDHKeyPair(RANDOM);
        assertEquals(keypair.getPublic(), fromBigInteger(keypair.getPublic().getY()));
    }

    @Test(expected = NullPointerException.class)
    public void testVerifyDHPublicKeyNull() throws OtrCryptoException {
        verifyDHPublicKey(null);
    }

    @Test(expected = OtrCryptoException.class)
    public void testIllegalDHPublicKey() throws OtrCryptoException {
        final DHPublicKey publicKey = fromBigInteger(ONE);
        verifyDHPublicKey(publicKey);
    }

    @Test(expected = OtrCryptoException.class)
    public void testPreventUseOfIllegalPublicKeyToGenerateSecretTooSmall() throws OtrCryptoException {
        final DHKeyPairOTR3 keypair = generateDHKeyPair(RANDOM);
        final DHPublicKey illegalPublicKey = fromBigInteger(ONE);
        keypair.generateSharedSecret(illegalPublicKey);
    }

    @Test(expected = OtrCryptoException.class)
    public void testPreventUseOfIllegalPublicKeyToGenerateSecretTooLarge() throws OtrCryptoException {
        final DHKeyPairOTR3 keypair = generateDHKeyPair(RANDOM);
        final DHPublicKey illegalPublicKey = fromBigInteger(MODULUS);
        keypair.generateSharedSecret(illegalPublicKey);
    }
}