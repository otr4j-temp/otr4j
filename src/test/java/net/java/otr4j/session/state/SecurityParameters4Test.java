/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.otr4j.session.state;

import net.java.otr4j.api.ClientProfile;
import net.java.otr4j.api.InstanceTag;
import net.java.otr4j.crypto.DHKeyPair;
import net.java.otr4j.crypto.ed448.ECDHKeyPair;
import net.java.otr4j.crypto.ed448.EdDSAKeyPair;
import net.java.otr4j.crypto.ed448.Point;
import net.java.otr4j.session.state.SecurityParameters4;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

import static java.util.Collections.singleton;
import static net.java.otr4j.api.Session.Version.FOUR;
import static net.java.otr4j.crypto.DSAKeyPair.generateDSAKeyPair;
import static net.java.otr4j.session.state.SecurityParameters4.Component.OURS;
import static net.java.otr4j.session.state.SecurityParameters4.Component.THEIRS;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("ConstantConditions")
public class SecurityParameters4Test {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ECDHKeyPair ecdhKeyPair = ECDHKeyPair.generate(RANDOM);

    private final DHKeyPair dhKeyPair = DHKeyPair.generate(RANDOM);

    private final ClientProfile profile1 = generateProfile();
    private final ClientProfile profile2 = generateProfile();

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullComponentFails() {
        new SecurityParameters4(null, ecdhKeyPair, dhKeyPair, ecdhKeyPair.getPublicKey(),
                dhKeyPair.getPublicKey(), profile1, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullECDHKeyPairFails() {
        new SecurityParameters4(OURS, null, dhKeyPair, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey(),
                profile1, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullDHKeyPairFails() {
        new SecurityParameters4(THEIRS, ecdhKeyPair, null, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey(),
                profile1, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullXFails() {
        new SecurityParameters4(OURS, ecdhKeyPair, dhKeyPair, null, dhKeyPair.getPublicKey(), profile1, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullAFails() {
        new SecurityParameters4(THEIRS, ecdhKeyPair, dhKeyPair, ecdhKeyPair.getPublicKey(), null, profile1, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullOurProfileFails() {
        new SecurityParameters4(THEIRS, ecdhKeyPair, dhKeyPair, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey(), null, profile2);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructionWithNullTheirProfileFails() {
        new SecurityParameters4(THEIRS, ecdhKeyPair, dhKeyPair, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey(), profile1, null);
    }

    @Test
    public void testConstruction() {
        final Point x = ecdhKeyPair.getPublicKey();
        final BigInteger a = dhKeyPair.getPublicKey();
        final SecurityParameters4 params = new SecurityParameters4(THEIRS, ecdhKeyPair, dhKeyPair, x, a, profile1, profile2);
        assertEquals(THEIRS, params.getInitializationComponent());
        assertEquals(x, params.getTheirECDHPublicKey());
        assertEquals(a, params.getTheirDHPublicKey());
        assertEquals(profile1, params.getOurProfile());
        assertEquals(profile2, params.getTheirProfile());
    }

    private static ClientProfile generateProfile() {
        return new ClientProfile(InstanceTag.random(RANDOM), EdDSAKeyPair.generate(RANDOM).getPublicKey(),
                EdDSAKeyPair.generate(RANDOM).getPublicKey(), singleton(FOUR), generateDSAKeyPair().getPublic());
    }
}