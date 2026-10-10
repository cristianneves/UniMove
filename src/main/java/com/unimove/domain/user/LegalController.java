package com.unimove.domain.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/legal")
@Tag(name = "Legal", description = "Termos de uso e política de privacidade")
public class LegalController {

    private final PrivacyProperties props;

    public LegalController(PrivacyProperties props) {
        this.props = props;
    }

    public record TermsResponse(String version, String policyUrl) {}

    @GetMapping("/terms")
    @Operation(summary = "Versão vigente dos termos e link da política (público)",
            description = "O app mostra o link no cadastro e envia `termsVersion` com esta versão.")
    public TermsResponse terms() {
        return new TermsResponse(props.termsVersion(), props.policyUrl());
    }
}
