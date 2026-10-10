package com.unimove.domain.user.document;

import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.document.dto.DriverDocumentItem;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP do cadastro documental (filtros desligados, como no resto da suite). */
@WebMvcTest(controllers = {DriverDocumentController.class, AdminDriverDocumentController.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(com.unimove.shared.exception.GlobalExceptionHandler.class)
class DriverDocumentControllerWebMvcTest {

    @Autowired MockMvc mvc;

    @MockBean DriverDocumentService service;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean DriverService driverService;

    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(driverId, "m@x.com", Role.MOTORISTA, "remanso-ba");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("upload multipart → 201 com o item")
    void uploadReturns201() throws Exception {
        when(service.upload(eq(driverId), eq(DriverDocumentType.CNH), any())).thenReturn(new DriverDocumentItem(
                UUID.randomUUID(), DriverDocumentType.CNH, DriverDocumentStatus.SUBMITTED, null,
                "image/jpeg", 10, Instant.now(), null));

        mvc.perform(multipart("/drivers/me/documentation/documents/CNH")
                        .file(new MockMultipartFile("file", "cnh.jpg", "image/jpeg", DocumentFileInspectorTest.JPEG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    @Test
    @DisplayName("upload sem a parte 'file' → 400")
    void uploadWithoutFile() throws Exception {
        mvc.perform(multipart("/drivers/me/documentation/documents/CNH"))
                .andExpect(status().isBadRequest());
        verify(service, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("tipo de documento desconhecido no path → 400 (nao 500)")
    void unknownDocumentType() throws Exception {
        mvc.perform(multipart("/drivers/me/documentation/documents/RG")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", DocumentFileInspectorTest.JPEG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'type'"));
    }

    @Test
    @DisplayName("perfil com CPF invalido e placa fora do padrao → 400 com campos")
    void profileValidation() throws Exception {
        String body = """
                {"cpf":"111.111.111-11","cnhNumber":"123","cnhCategory":"Z","cnhExpiresOn":"2030-01-01",
                 "vehicleBrand":"Fiat","vehicleModel":"Uno","vehicleYear":2015,"vehicleColor":"Prata",
                 "vehiclePlate":"XX"}
                """;

        mvc.perform(put("/drivers/me/documentation/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cpfValid").exists())
                .andExpect(jsonPath("$.fieldErrors.cnhNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.cnhCategory").exists())
                .andExpect(jsonPath("$.fieldErrors.vehiclePlate").exists());
    }

    @Test
    @DisplayName("admin reprova sem motivo → 400")
    void rejectRequiresReason() throws Exception {
        mvc.perform(post("/admin/drivers/" + driverId + "/documents/" + UUID.randomUUID() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).reject(any(), any(), any(), any());
    }
}
