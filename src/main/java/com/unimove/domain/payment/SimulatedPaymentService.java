package com.unimove.domain.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
class SimulatedPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentService.class);

    private static final String MERCHANT_NAME = "UNIMOVE";
    private static final String MERCHANT_CITY = "SAO PAULO";
    private static final String PIX_KEY = "unimove@example.com";

    @Override
    public String generatePixPayload(UUID rideId, BigDecimal valor) {
        String txid = rideId.toString().replace("-", "").substring(0, 25);
        String valorStr = valor.toPlainString();

        String payload = "00020126"
                + tlv("00", "BR.GOV.BCB.PIX")
                + tlv("01", PIX_KEY)
                + tlv("52", "0000")
                + tlv("53", "986")
                + tlv("54", valorStr)
                + tlv("58", "BR")
                + tlv("59", MERCHANT_NAME)
                + tlv("60", MERCHANT_CITY)
                + tlv("62", tlv("05", txid))
                + "6304SIMU";

        log.debug("Pix payload simulado gerado para ride {}", rideId);
        return payload;
    }

    private static String tlv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }
}
