package com.awin.transactions.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awin.transactions.outbox.MessagePublisher;
import com.awin.transactions.support.RecordingMessagePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "awin.outbox.poll-interval-ms=3600000")
@AutoConfigureMockMvc
class TransactionControllerTest {

    @TestConfiguration
    static class TestPublisherConfig {
        @Bean
        @Primary
        MessagePublisher recordingMessagePublisher() {
            return new RecordingMessagePublisher();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String VALID_CREATE =
            """
            {
              "saleAmount": "100.00",
              "commissionAmount": "10.00",
              "parts": [
                {"saleAmount":"60.00","commissionAmount":"6.00"},
                {"saleAmount":"40.00","commissionAmount":"4.00"}
              ]
            }
            """;

    @Test
    void createThenApprove() throws Exception {
        MvcResult result = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andReturn();

        String id = extractId(result.getResponse().getContentAsString());

        mvc.perform(post("/transactions/" + id + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/transactions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void declinePendingTransaction() throws Exception {
        MvcResult result = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andReturn();
        String id = extractId(result.getResponse().getContentAsString());

        mvc.perform(post("/transactions/" + id + "/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void approveTwiceReturnsConflict() throws Exception {
        MvcResult result = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andReturn();
        String id = extractId(result.getResponse().getContentAsString());

        mvc.perform(post("/transactions/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(post("/transactions/" + id + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"));
    }

    @Test
    void declineAfterApproveReturnsConflict() throws Exception {
        MvcResult result = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andReturn();
        String id = extractId(result.getResponse().getContentAsString());

        mvc.perform(post("/transactions/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(post("/transactions/" + id + "/decline"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMismatchedSums() throws Exception {
        String body =
                """
                {
                  "saleAmount":"100.00",
                  "commissionAmount":"10.00",
                  "parts":[{"saleAmount":"50.00","commissionAmount":"6.00"}]
                }
                """;
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail",
                        equalTo("Sum of part saleAmounts (50.00) does not equal transaction saleAmount (100.00)")));
    }

    @Test
    void rejectsEmptyParts() throws Exception {
        String body =
                """
                {
                  "saleAmount":"100.00",
                  "commissionAmount":"10.00",
                  "parts":[]
                }
                """;
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCommissionGreaterThanSale() throws Exception {
        String body =
                """
                {
                  "saleAmount":"100.00",
                  "commissionAmount":"200.00",
                  "parts":[{"saleAmount":"100.00","commissionAmount":"200.00"}]
                }
                """;
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveUnknownIdReturns404() throws Exception {
        mvc.perform(post("/transactions/00000000-0000-0000-0000-000000000000/approve"))
                .andExpect(status().isNotFound());
    }

    private String extractId(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        return node.get("id").asText();
    }
}
