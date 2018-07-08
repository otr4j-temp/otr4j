/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.session;

import net.java.otr4j.api.OtrEngineHost;
import net.java.otr4j.api.Session;
import net.java.otr4j.api.SessionID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.LinkedList;

import static java.util.Objects.requireNonNull;

/**
 * OTR fragmenter.
 * 
 * @author Danny van Heumen
 */
// FIXME extend with support for OTRv4: https://github.com/otrv4/otrv4/blob/master/otrv4.md#fragmentation
// TODO BUG: Fragmenter is based on allowed capabilities, not negotiated protocol version for session.
final class OtrFragmenter {

    /**
     * Exception message in cases where only OTRv1 is allowed.
     */
    private static final String OTRV1_NOT_SUPPORTED = "Fragmentation is not supported in OTRv1.";

    /**
     * The maximum number of fragments supported by the OTR (v3) protocol.
     */
    private static final int MAXIMUM_NUMBER_OF_FRAGMENTS = 65535;

    /**
     * The message format of an OTRv3 message fragment.
     */
    private static final String OTRV3_MESSAGE_FRAGMENT_FORMAT = "?OTR|%08x|%08x,%05d,%05d,%s,";

    /**
     * The message format of an OTRv2 message fragment.
     */
    private static final String OTRV2_MESSAGE_FRAGMENT_FORMAT = "?OTR,%d,%d,%s,";

    /**
     * Instructions on how to fragment the input message.
     */
    private final OtrEngineHost host;

    private final SessionID sessionID;

    private final int senderTag;

    private final int receiverTag;

    /**
     * Constructor.
     *
     * @param host OTR engine host calling upon OTR session
     */
    OtrFragmenter(@Nonnull final OtrEngineHost host, @Nonnull final SessionID sessionID, final int senderTag, final int receiverTag) {
        this.host = requireNonNull(host, "host cannot be null");
        this.sessionID = requireNonNull(sessionID);
        this.senderTag = senderTag;
        this.receiverTag = receiverTag;
    }

    /**
     * Calculate the number of fragments that are required for the message to be
     * sent fragmented completely.
     *
     * @param version the negotiated protocol version
     * @param message
     *            the original message
     * @return returns the number of fragments required
     * @throws IOException
     *             throws an IOException in case fragment size is too small to
     *             store any content or when the provided policy does not
     *             support fragmentation, for example if only OTRv1 is allowed.
     */
    int numberOfFragments(final int version, @Nonnull final String message) throws IOException {
        if (version == Session.OTRv.ONE) {
            throw new UnsupportedOperationException(OTRV1_NOT_SUPPORTED);
        }
        final int fragmentSize = this.host.getMaxFragmentSize(this.sessionID);
        if (version < Session.OTRv.TWO || fragmentSize >= message.length()) {
            return 1;
        }
        return computeFragmentNumber(version, message, fragmentSize);
    }

    /**
     * Compute the number of fragments required.
     *
     * @param message the original message
     * @param fragmentSize size of fragments
     * @return returns number of fragments required.
     * @throws IOException throws an IOException if fragment size is too small.
     */
    private int computeFragmentNumber(final int version, @Nonnull final String message, final int fragmentSize)
        throws IOException {
        final int overhead = computeHeaderSize(version);
        final int payloadSize = fragmentSize - overhead;
        if (payloadSize <= 0) {
            throw new IOException("Fragment size too small for storing content.");
        }
        int messages = message.length() / payloadSize;
        if (message.length() % payloadSize != 0) {
            messages++;
        }
        return messages;
    }

    /**
     * Fragment the given message into pieces.
     *
     * @param version the session's negotiated protocol version
     * @param message the original message
     * @return returns an array of message fragments. The array will contain at
     * least 1 message fragment, or more if fragmentation is necessary.
     * @throws IOException throws an IOException if the fragment size is too small or if
     *                     the maximum number of fragments is exceeded.
     */
    // TODO verify that we fragment an original message, not a message that is fragmented itself.
    @Nonnull
    String[] fragment(final int version, @Nonnull final String message) throws IOException {
        if (version == 1) {
            throw new UnsupportedOperationException(OTRV1_NOT_SUPPORTED);
        }
        final int fragmentSize = this.host.getMaxFragmentSize(this.sessionID);
        return fragment(version, message, fragmentSize);
    }

    /**
     * Fragment a message according to the specified instructions.
     *
     * @param version      current session's negotiated protocol version
     * @param message      the message
     * @param fragmentSize the maximum fragment size
     * @return Returns the fragmented message. The array will contain at least 1
     * message fragment, or more if fragmentation is necessary.
     * @throws IOException Exception in the case when it is impossible to fragment the
     *                     message according to the specified instructions.
     */
    @Nonnull
    private String[] fragment(final int version, @Nonnull final String message, final int fragmentSize) throws IOException {
        if (version < Session.OTRv.TWO || fragmentSize >= message.length()) {
            return new String[] { message };
        }
        final int num = computeFragmentNumber(version, message, fragmentSize);
        if (num > MAXIMUM_NUMBER_OF_FRAGMENTS) {
            throw new IOException("Number of necessary fragments exceeds limit.");
        }
        final int payloadSize = fragmentSize - computeHeaderSize(version);
        int previous = 0;
        final LinkedList<String> fragments = new LinkedList<>();
        while (previous < message.length()) {
            // Either get new position or position of exact message end
            final int end = Math.min(previous + payloadSize, message.length());

            final String partialContent = message.substring(previous, end);
            fragments.add(createMessageFragment(version, fragments.size(), num, partialContent));

            previous = end;
        }
        return fragments.toArray(new String[fragments.size()]);
    }

    /**
     * Create a message fragment.
     *
     * @param count          the current fragment number
     * @param total          the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    @Nonnull
    private String createMessageFragment(final int version, final int count, final int total,
                                         @Nonnull final String partialContent) {
        switch (version) {
            case Session.OTRv.TWO:
                return createV2MessageFragment(count, total, partialContent);
            case Session.OTRv.THREE:
                return createV3MessageFragment(count, total, partialContent);
            case Session.OTRv.FOUR:
                // FIXME implement OTRv4 support.
                throw new UnsupportedOperationException("Protocol version 4 support has not been implemented yet.");
            default:
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
        }
    }

    /**
     * Create a message fragment according to the v3 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    @Nonnull
    private String createV3MessageFragment(final int count, final int total, @Nonnull final String partialContent) {
        return String.format(OTRV3_MESSAGE_FRAGMENT_FORMAT, this.senderTag, this.receiverTag, count + 1, total,
            partialContent);
    }

    /**
     * Create a message fragment according to the v2 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    @Nonnull
    private String createV2MessageFragment(final int count, final int total, @Nonnull final String partialContent) {
        return String.format(OTRV2_MESSAGE_FRAGMENT_FORMAT, count + 1, total, partialContent);
    }

    /**
     * Compute size of fragmentation header size.
     *
     * @return returns size of fragment header
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    private int computeHeaderSize(final int version) {
        switch (version) {
            case Session.OTRv.TWO:
                return computeHeaderV2Size();
            case Session.OTRv.THREE:
                return computeHeaderV3Size();
            case Session.OTRv.FOUR:
                // FIXME implement OTRv4 support.
                throw new UnsupportedOperationException("Protocol version 4 support has not been implemented yet.");
            default:
                throw new UnsupportedOperationException("Unsupported protocol version: " + version);
        }
    }

    /**
     * Compute the overhead size for a v3 header.
     *
     * @return returns size of v3 header
     */
    static int computeHeaderV3Size() {
        // For a OTRv3 header this seems to be a constant number, since the
        // specs seem to suggest that smaller numbers have leading zeros.
        return 36;
    }

    /**
     * Compute the overhead size for a v2 header.
     *
     * Current implementation returns an upper bound size for the size of the
     * header. As I understand it, the protocol does not require leading zeros
     * to fill a 5-space number are so in theory it is possible to gain a few
     * extra characters per message if an exact calculation of the number of
     * required chars is used.
     *
     * @return returns size of v2 header
     */
    static int computeHeaderV2Size() {
        // currently returns an upper bound (for the case of 10000+ fragments)
        return 18;
    }
}
