package io.github.alexc.keycloak.ageproofing.model;

import java.time.Instant;

/**
 * Immutable record representing the zero-PII cryptographic result of an age/identity proofing check.
 *
 * <p>Under GDPR Article 5 and 25 (Privacy by Design &amp; Data Minimization), this record never stores
 * personal identification details, real dates of birth, full names, or document numbers.
 * Only the SHA-256 hash of the verification presentation, the issuing authority, and the timestamp
 * are retained for audit and non-repudiation purposes.
 *
 * @param success       true if the cryptographic assertion was verified and meets the age threshold
 * @param proofHash     SHA-256 cryptographic digest of the received assertion token
 * @param issuer        identifier/URI of the trusted identity provider or digital wallet issuer
 * @param verifiedAt    epoch seconds when the verification occurred
 * @param errorMessage  human-readable error description in case of verification failure (null if success)
 */
public record VerificationEvidence(
        boolean success,
        String proofHash,
        String issuer,
        long verifiedAt,
        String errorMessage
) {

    /**
     * Constructs a successful verification evidence record.
     *
     * @param proofHash SHA-256 digest of the verified assertion
     * @param issuer    URI or identifier of the issuer
     * @return successful VerificationEvidence instance
     */
    public static VerificationEvidence successful(String proofHash, String issuer) {
        return new VerificationEvidence(
                true,
                proofHash,
                issuer,
                Instant.now().getEpochSecond(),
                null
        );
    }

    /**
     * Constructs a failed verification evidence record.
     *
     * @param errorMessage reason for failure
     * @return failed VerificationEvidence instance
     */
    public static VerificationEvidence failed(String errorMessage) {
        return new VerificationEvidence(
                false,
                null,
                null,
                Instant.now().getEpochSecond(),
                errorMessage
        );
    }
}
