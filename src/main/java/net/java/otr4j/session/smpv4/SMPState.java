/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.session.smpv4;

import net.java.otr4j.crypto.ed448.Scalar;
import net.java.otr4j.session.api.SMPStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The interface defining the state machine capabilities, as per the State pattern.
 */
interface SMPState {

    /**
     * Get the current status of the SMP state machine.
     *
     * @return Returns the current status.
     */
    @Nonnull
    SMPStatus getStatus();

    /**
     * Initiate a new SMP negotiation.
     * <p>
     * This may be called at any time. If a SMP negotiation is already in progress, it will be aborted.
     *
     * @param context  the SMP context
     * @param question the question posed to the other party
     * @param secret   the secret MPI derived from the user-supplied answer
     * @return Returns the SMP initiating message to be included as TLV payload in a data message.
     * @throws SMPAbortException Indicates that a running SMP negotiation is aborted.
     */
    @Nonnull
    SMPMessage1 initiate(SMPContext context, String question, Scalar secret) throws SMPAbortException;

    /**
     * Respond to SMP initiation request with response based on our own secret answer.
     * <p>
     * The question is repeated here to ensure that the answer corresponds to the currently known question. If the
     * question mismatches with the currently known question, then this answer is ignored to ensure that we do not
     * follow the SMP negotiation based on a wrong answer.
     *
     * @param context  the SMP context
     * @param question the question posed by the other party to which we are providing the answer.
     * @param secret   the secret MPI derived from the user-supplied answer
     * @return Returns SMP response message to be included as TLV payload in a data message. In case no SMP init is
     * active, or the response is not for the current one, then return {@code null}.
     */
    @Nullable
    SMPMessage2 respondWithSecret(SMPContext context, String question, Scalar secret);

    /**
     * Process a (non-initial) SMP message.
     *
     * @param context the SMP context
     * @param message the message
     * @return Returns the response to the received message.
     * @throws SMPAbortException In case of an unexpected message, the protocol gets reset to the initial state.
     */
    @Nullable
    SMPMessage process(SMPContext context, SMPMessage message) throws SMPAbortException;
}
