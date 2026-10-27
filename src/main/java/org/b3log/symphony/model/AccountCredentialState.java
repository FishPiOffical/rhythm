package org.b3log.symphony.model;

/** Independent from user whole-row writes; never project credential versions to public user DTOs. */
public final class AccountCredentialState {
    private AccountCredentialState() { }
    public static final String TABLE="account_credential_state";
    public static final String EPOCH="credentialEpoch";
}
