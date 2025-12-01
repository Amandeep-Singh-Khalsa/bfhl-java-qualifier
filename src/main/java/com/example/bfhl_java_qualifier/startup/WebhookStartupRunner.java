package com.example.bfhl_java_qualifier.startup;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.bfhl_java_qualifier.config.BfhlProperties;
import com.example.bfhl_java_qualifier.config.CandidateProperties;
import com.example.bfhl_java_qualifier.sql.SqlSolver;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class WebhookStartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WebhookStartupRunner.class);

    private final RestTemplate restTemplate;
    private final CandidateProperties candidateProperties;
    private final BfhlProperties bfhlProperties;
    private final SqlSolver sqlSolver;

    public WebhookStartupRunner(RestTemplate restTemplate,
                                CandidateProperties candidateProperties,
                                BfhlProperties bfhlProperties,
                                SqlSolver sqlSolver) {
        this.restTemplate = restTemplate;
        this.candidateProperties = candidateProperties;
        this.bfhlProperties = bfhlProperties;
        this.sqlSolver = sqlSolver;
    }

    @Override
    public void run(String... args) {
        log.info(">>> WebhookStartupRunner started");

        try {
            // 1. Generate webhook
            String generateUrl = bfhlProperties.getBaseUrl()
                    + "/generateWebhook/" + bfhlProperties.getLanguage();
            log.info("Calling generateWebhook: {}", generateUrl);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", candidateProperties.getName());
            requestBody.put("regNo", candidateProperties.getRegNo());
            requestBody.put("email", candidateProperties.getEmail());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    generateUrl, entity, JsonNode.class);

            log.info("generateWebhook status={}, body={}",
                    response.getStatusCode(), response.getBody());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("generateWebhook failed: status={}, body={}",
                        response.getStatusCode(), response.getBody());
                return;
            }

            JsonNode body = response.getBody();
            String webhookUrl = body.path("webhook").asText(null);
            String accessToken = body.path("accessToken").asText(null);

            log.info("Received webhookUrl={}, accessTokenPresent={}",
                    webhookUrl, accessToken != null);

            if (accessToken == null) {
                log.error("accessToken missing in response");
                return;
            }

            // 2. Get SQL query (we ignore odd/even inside implementation)
            String finalQuery = sqlSolver.getFinalQuery(true);
            log.info("Final SQL query:\n{}", finalQuery);

            // 3. Submit final query
            String submitUrl = bfhlProperties.getBaseUrl()
                    + "/testWebhook/" + bfhlProperties.getLanguage();
            log.info("Submitting finalQuery to: {}", submitUrl);

            HttpHeaders submitHeaders = new HttpHeaders();
            submitHeaders.setContentType(MediaType.APPLICATION_JSON);

            // Problem statement says Authorization: <accessToken>
            submitHeaders.set("Authorization", accessToken);

            Map<String, String> submitBody = new HashMap<>();
            submitBody.put("finalQuery", finalQuery);

            HttpEntity<Map<String, String>> submitEntity =
                    new HttpEntity<>(submitBody, submitHeaders);

            ResponseEntity<String> submitResponse = restTemplate.postForEntity(
                    submitUrl, submitEntity, String.class);

            log.info("Submit response: status={}, body={}",
                    submitResponse.getStatusCode(), submitResponse.getBody());

        } catch (Exception ex) {
            log.error("Error during webhook flow", ex);
        }

        log.info(">>> WebhookStartupRunner finished");
    }
}
