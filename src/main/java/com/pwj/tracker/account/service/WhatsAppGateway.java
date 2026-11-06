package com.pwj.tracker.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Thin client for the wa-gateway Node service (QR-linked WhatsApp Web session). */
@Component
public class WhatsAppGateway {

    private final RestTemplate http = new RestTemplate();
    private final String url;
    private final String key;

    public WhatsAppGateway(@Value("${pwj.whatsapp.gateway.url:http://localhost:3100}") String url,
                           @Value("${pwj.whatsapp.gateway.key:dev-gateway-key}") String key) {
        this.url = url.replaceAll("/$", "");
        this.key = key;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Gateway-Key", key);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** {status: starting|qr|connected|disconnected|unreachable, qr?, phone?} */
    @SuppressWarnings("unchecked")
    public Map<String, Object> status() {
        try {
            return http.exchange(url + "/status", HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
        } catch (Exception e) {
            return Map.of("status", "unreachable");
        }
    }

    public boolean connected() { return "connected".equals(status().get("status")); }

    /** @throws RuntimeException with the gateway's reason when the message could not be sent */
    @SuppressWarnings("unchecked")
    public void send(String phone, String message, String card) {
        try {
            Map<String, Object> body = new java.util.HashMap<>(Map.of("phone", phone, "message", message));
            if (card != null && !card.isBlank()) body.put("card", card.toLowerCase());
            http.exchange(url + "/send", HttpMethod.POST, new HttpEntity<>(body, headers()), Map.class);
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            throw new RuntimeException(body.contains("\"error\"") ? body.replaceAll(".*\"error\":\"([^\"]*)\".*", "$1") : "Gateway error " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new RuntimeException("WhatsApp gateway is not reachable");
        }
    }

    /** The greeting card PNG for the form preview, or null if the gateway is down / the card is unknown. */
    public byte[] card(String theme) {
        try {
            return http.exchange(url + "/card/" + theme.toLowerCase(), HttpMethod.GET, new HttpEntity<>(headers()), byte[].class).getBody();
        } catch (Exception e) { return null; }
    }

    public void logout() {
        try { http.exchange(url + "/logout", HttpMethod.POST, new HttpEntity<>("{}", headers()), Map.class); } catch (Exception ignored) { }
    }
}
