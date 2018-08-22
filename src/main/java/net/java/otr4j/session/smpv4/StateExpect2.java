package net.java.otr4j.session.smpv4;

import net.java.otr4j.session.api.SMPStatus;
import nl.dannyvanheumen.joldilocks.Ed448;
import nl.dannyvanheumen.joldilocks.Point;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SMP_VALUE_0x03;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SMP_VALUE_0x04;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SMP_VALUE_0x05;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SMP_VALUE_0x06;
import static net.java.otr4j.crypto.OtrCryptoEngine4.KDFUsage.SMP_VALUE_0x07;
import static net.java.otr4j.crypto.OtrCryptoEngine4.generateRandomValueInZq;
import static net.java.otr4j.crypto.OtrCryptoEngine4.hashToScalar;
import static net.java.otr4j.session.api.SMPStatus.INPROGRESS;
import static nl.dannyvanheumen.joldilocks.Ed448.basePoint;
import static nl.dannyvanheumen.joldilocks.Ed448.primeOrder;
import static org.bouncycastle.util.Arrays.concatenate;

final class StateExpect2 implements SMPState {

    private static final Logger LOGGER = Logger.getLogger(StateExpect2.class.getName());

    private final SecureRandom random;

    private final BigInteger secret;
    private final BigInteger a2;
    private final BigInteger a3;

    StateExpect2(@Nonnull final SecureRandom random, @Nonnull final BigInteger secret, @Nonnull final BigInteger a2,
            @Nonnull final BigInteger a3) {
        this.random = requireNonNull(random);
        this.secret = requireNonNull(secret);
        this.a2 = requireNonNull(a2);
        this.a3 = requireNonNull(a3);
    }

    @Nonnull
    @Override
    public SMPStatus getStatus() {
        return INPROGRESS;
    }

    @Nonnull
    @Override
    public SMPMessage1 initiate(@Nonnull final SMPContext context, @Nonnull final String question,
            @Nonnull final BigInteger secret) {
        // FIXME implement SMP initiation in StateExpect1
        throw new UnsupportedOperationException("To be implemented");
    }

    @Nullable
    @Override
    public SMPMessage2 respondWithSecret(@Nonnull final SMPContext context, @Nonnull final String question, @Nonnull final BigInteger secret) {
        // Given that this is an action by the local user, we don't see this as a violation of the protocol. Therefore,
        // we don't abort.
        LOGGER.log(Level.WARNING, "Requested to respond with secret answer, but no request is pending. Ignoring request.");
        return null;
    }

    @Nonnull
    @Override
    public SMPMessage process(@Nonnull final SMPContext context, @Nonnull final SMPMessage message)
            throws SMPAbortException {
        if (!(message instanceof SMPMessage2)) {
            throw new SMPAbortException("Received unexpected SMP message in StateExpect2.");
        }
        final SMPMessage2 smp2 = (SMPMessage2) message;
        if (!Ed448.contains(smp2.g2b) || !Ed448.contains(smp2.g3b) || !Ed448.contains(smp2.pb)
                || !Ed448.contains(smp2.qb)) {
            throw new SMPAbortException("Message failed verification.");
        }
        final Point g = basePoint();
        if (!smp2.c2.equals(hashToScalar(SMP_VALUE_0x03, g.multiply(smp2.d2).add(smp2.g2b.multiply(smp2.c2)).encode()))) {
            throw new SMPAbortException("Message failed verification.");
        }
        if (!smp2.c3.equals(hashToScalar(SMP_VALUE_0x04, g.multiply(smp2.d3).add(smp2.g3b.multiply(smp2.c3)).encode()))) {
            throw new SMPAbortException("Message failed verification.");
        }
        final Point g2 = smp2.g2b.multiply(this.a2);
        final Point g3 = smp2.g3b.multiply(this.a3);
        if (!smp2.cp.equals(hashToScalar(SMP_VALUE_0x05, concatenate(
                g3.multiply(smp2.d5).add(smp2.pb.multiply(smp2.cp)).encode(),
                g.multiply(smp2.d5).add(g2.multiply(smp2.d6)).add(smp2.qb.multiply(smp2.cp)).encode())))) {
            throw new SMPAbortException("Message failed verification.");
        }
        final BigInteger r4 = generateRandomValueInZq(this.random);
        final BigInteger r5 = generateRandomValueInZq(this.random);
        final BigInteger r6 = generateRandomValueInZq(this.random);
        final BigInteger r7 = generateRandomValueInZq(this.random);
        final Point pa = g3.multiply(r4);
        final BigInteger q = primeOrder();
        final BigInteger secretModQ = this.secret.mod(q);
        final Point qa = g.multiply(r4).add(g2.multiply(secretModQ));
        final BigInteger cp = hashToScalar(SMP_VALUE_0x06, concatenate(g3.multiply(r5).encode(),
                g.multiply(r5).add(g2.multiply(r6)).encode()));
        final BigInteger d5 = r5.subtract(r4.multiply(cp)).mod(q);
        final BigInteger d6 = r6.subtract(secretModQ.multiply(cp)).mod(q);
        final Point ra = qa.add(smp2.qb.negate()).multiply(a3);
        final BigInteger cr = hashToScalar(SMP_VALUE_0x07, concatenate(g.multiply(r7).encode(),
                qa.add(smp2.qb.negate()).multiply(r7).encode()));
        final BigInteger d7 = r7.subtract(a3.multiply(cr)).mod(q);
        context.setState(new StateExpect4(this.random, this.a3, smp2.g3b, pa, smp2.pb, qa, smp2.qb));
        return new SMPMessage3(pa, qa, cp, d5, d6, ra, cr, d7);
    }
}