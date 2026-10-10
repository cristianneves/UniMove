package com.unimove.it;

import com.unimove.domain.user.User;
import com.unimove.domain.user.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class DriverDocumentIT extends IntegrationTestSupport {

    @Test
    @DisplayName("dois envios simultaneos da CNH → um 201, um 409 e um unico item na fila do admin")
    void concurrentUploadKeepsSingleSubmitted() throws IOException {
        User driver = newDriver(VehicleType.MOTO, false);
        String token = token(driver);
        byte[] jpeg = jpeg();

        List<Integer> result = statuses(List.of(
                sendAsync(upload(token, jpeg)),
                sendAsync(upload(token, jpeg))));

        assertThat(result).containsExactly(201, 409);
        Integer submitted = jdbc.queryForObject(
                "SELECT count(*) FROM driver_documents WHERE driver_id = ? AND type = 'CNH' AND status = 'SUBMITTED'",
                Integer.class, driver.getId());
        assertThat(submitted).isEqualTo(1);
    }

    private HttpRequest upload(String token, byte[] content) {
        String boundary = "it-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"cnh.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("/drivers/me/documentation/documents/CNH", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
    }

    private static byte[] jpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }
}
