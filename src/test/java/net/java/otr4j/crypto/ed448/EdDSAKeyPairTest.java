/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.crypto.ed448;

import org.junit.Test;

import java.security.SecureRandom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.java.otr4j.crypto.ed448.Ed448.multiplyByBase;
import static net.java.otr4j.crypto.ed448.EdDSAKeyPair.generate;
import static net.java.otr4j.crypto.ed448.EdDSAKeyPair.verify;
import static net.java.otr4j.util.ByteArrays.allZeroBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.internal.util.reflection.Whitebox.getInternalState;

@SuppressWarnings("ConstantConditions")
public final class EdDSAKeyPairTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test(expected = NullPointerException.class)
    public void testGenerateNullRandom() {
        generate(null);
    }

    @Test
    public void testGenerateKeyPair() {
        assertNotNull(generate(RANDOM));
    }

    @Test
    public void testRegeneratePublicKey() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        final Point expected = keypair.getPublicKey();
        final Point generated = multiplyByBase(keypair.getSecretKey());
        assertEquals(expected, generated);
    }

    @Test(expected = NullPointerException.class)
    public void testSignNullMessage() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        keypair.sign(null);
    }

    @Test(expected = NullPointerException.class)
    public void testVerifyNullPublicKey() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        verify(null, message, sig);
    }

    @Test(expected = NullPointerException.class)
    public void testVerifyNullMessage() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        verify(keypair.getPublicKey(), null, sig);
    }

    @Test(expected = NullPointerException.class)
    public void testVerifyNullSignature() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        verify(keypair.getPublicKey(), message, null);
    }

    @Test
    public void testSignatureIsVerifiable() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        verify(keypair.getPublicKey(), message, sig);
    }

    @Test(expected = ValidationException.class)
    public void testVerifyWrongPublicKey() throws ValidationException {
        final EdDSAKeyPair keypair2 = EdDSAKeyPair.generate(RANDOM);
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        verify(keypair2.getPublicKey(), message, sig);
    }

    @Test(expected = ValidationException.class)
    public void testVerifyWrongMessage() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        verify(keypair.getPublicKey(), "bladkfjsaf".getBytes(UTF_8), sig);
    }

    @Test(expected = ValidationException.class)
    public void testVerifyWrongSignature() throws ValidationException {
        final byte[] message = "SomeRandomMessage".getBytes(UTF_8);
        final EdDSAKeyPair keypair = generate(RANDOM);
        final byte[] sig = keypair.sign(message);
        sig[0] = 0;
        verify(keypair.getPublicKey(), message, sig);
    }

    @Test
    public void testClose() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        keypair.close();
        assertTrue(allZeroBytes((byte[]) getInternalState(keypair, "symmetricKey")));
    }

    @Test
    public void testGetPublicKeyAfterClose() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        keypair.close();
        assertNotNull(keypair.getPublicKey());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSecretKeyAfterClose() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        keypair.close();
        keypair.getSecretKey();
    }

    @Test(expected = IllegalStateException.class)
    public void testSignAfterClose() {
        final EdDSAKeyPair keypair = generate(RANDOM);
        keypair.close();
        keypair.sign("SomeRandomMessage".getBytes(UTF_8));
    }
}
