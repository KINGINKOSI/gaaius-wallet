package com.gaaiuswallet.app.entity.attestation;

import com.gaaiuswallet.app.entity.tokens.TokenCardMeta;

public interface AttestationImportInterface
{
    void attestationImported(TokenCardMeta newToken);
    void importError(String error);
    void smartPassValidation(SmartPassReturn validation);
}
