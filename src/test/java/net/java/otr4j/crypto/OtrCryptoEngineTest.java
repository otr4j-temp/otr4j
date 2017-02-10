/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.otr4j.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for OtrCryptoEngine.
 *
 * @author Danny van Heumen
 */
public class OtrCryptoEngineTest {

    private static final SecureRandom RAND = new SecureRandom();

    @Test
    public void testGeneratedSharedSecretEqual() throws OtrCryptoException {
        final KeyPair aliceDHKeyPair = OtrCryptoEngine.generateDHKeyPair(RAND);
        final KeyPair bobDHKeyPair = OtrCryptoEngine.generateDHKeyPair(RAND);

        assertEquals(OtrCryptoEngine.generateSecret(aliceDHKeyPair.getPrivate(), bobDHKeyPair.getPublic()),
                OtrCryptoEngine.generateSecret(bobDHKeyPair.getPrivate(), aliceDHKeyPair.getPublic()));
    }

    @Test
    public void testRandomBehavesExpectedly() {
        final byte[] rand1 = OtrCryptoEngine.random(RAND, new byte[24]);
        final byte[] rand2 = OtrCryptoEngine.random(RAND, new byte[24]);
        final byte[] rand3 = OtrCryptoEngine.random(RAND, new byte[24]);
        final byte[] rand4 = OtrCryptoEngine.random(RAND, new byte[24]);
        assertFalse(Arrays.equals(rand1, rand2));
        assertFalse(Arrays.equals(rand1, rand3));
        assertFalse(Arrays.equals(rand1, rand4));
        assertFalse(Arrays.equals(rand2, rand3));
        assertFalse(Arrays.equals(rand2, rand4));
        assertFalse(Arrays.equals(rand3, rand4));
    }

    @Test
    public void testCheckEqualsEqualArrays() throws OtrCryptoException {
        final byte[] a = new byte[]{'a','b','c','d','e'};
        OtrCryptoEngine.checkEquals(a, a, "Expected array to be equal.");
    }

    @Test(expected = OtrCryptoException.class)
    public void testCheckEqualsArrayLengthDiff1() throws OtrCryptoException {
        final byte[] a = new byte[]{'a', 'a', 'a'};
        final byte[] b = new byte[]{'a', 'a', 'a', 'a'};
        OtrCryptoEngine.checkEquals(a, b, "Expected array to be equal.");
    }

    @Test(expected = OtrCryptoException.class)
    public void testCheckEqualsArrayLengthDiff2() throws OtrCryptoException {
        final byte[] a = new byte[]{'a', 'a', 'a', 'a'};
        final byte[] b = new byte[]{'a', 'a', 'a'};
        OtrCryptoEngine.checkEquals(a, b, "Expected array to be equal.");
    }

    @Test(expected = OtrCryptoException.class)
    public void testCheckEqualsArrayContentDiff() throws OtrCryptoException {
        final byte[] a = new byte[]{'a', 'b', 'c', 'd'};
        final byte[] b = new byte[]{'a', 'b', 'c', 'e'};
        OtrCryptoEngine.checkEquals(a, b, "Expected array to be equal.");
    }

    @Test
    public void testCheckEqualsNullArraysEqual() throws OtrCryptoException {
        OtrCryptoEngine.checkEquals(null, null, "Expected array to be equal.");
    }

    @Test(expected = OtrCryptoException.class)
    public void testCheckEqualsOneNull1() throws OtrCryptoException {
        final byte[] a = new byte[]{'a', 'a', 'a', 'a'};
        OtrCryptoEngine.checkEquals(a, null, "Expected array to be equal.");
    }

    @Test(expected = OtrCryptoException.class)
    public void testCheckEqualsOneNull2() throws OtrCryptoException {
        final byte[] b = new byte[]{'a', 'a', 'a', 'a'};
        OtrCryptoEngine.checkEquals(null, b, "Expected array to be equal.");
    }
}
