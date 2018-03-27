package net.java.otr4j.io.messages;

import net.java.otr4j.crypto.ECDHKeyPair;
import net.java.otr4j.profile.UserProfile;
import net.java.otr4j.profile.UserProfileTestUtils;
import nl.dannyvanheumen.joldilocks.Point;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

import static nl.dannyvanheumen.joldilocks.Ed448.basePoint;

public class IdentityMessageTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final UserProfileTestUtils profileTestUtils = new UserProfileTestUtils();

    @Test
    public void testConstructIdentityMessage() {
        final ECDHKeyPair ecKeyPair = ECDHKeyPair.generate(RANDOM);
        final UserProfile userProfile = profileTestUtils.createTransitionalUserProfile();
        final Point y = basePoint();
        final BigInteger b = BigInteger.TEN;
        new IdentityMessage(4, 0, 0, userProfile, y, b);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionNullUserProfile() {
        final Point y = basePoint();
        final BigInteger b = BigInteger.TEN;
        new IdentityMessage(4, 0, 0, null, y, b);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructIdentityMessageNullY() {
        final UserProfile userProfile = profileTestUtils.createUserProfile();
        final BigInteger b = BigInteger.TEN;
        new IdentityMessage(4, 0, 0, userProfile, null, b);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructIdentityMessageNullB() {
        final UserProfile userProfile = profileTestUtils.createUserProfile();
        final Point y = basePoint();
        new IdentityMessage(4, 0, 0, userProfile, y, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructTooLowProtocolVersion() {
        final UserProfile userProfile = profileTestUtils.createUserProfile();
        final Point y = basePoint();
        final BigInteger b = BigInteger.TEN;
        new IdentityMessage(3, 0, 0, userProfile, y, b);
    }
}