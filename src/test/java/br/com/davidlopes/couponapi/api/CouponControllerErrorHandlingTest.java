package br.com.davidlopes.couponapi.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String payload(LinkedHashMap<String, Object> overrides) throws Exception {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("code", "AB12CD");
        body.put("description", "10% off");
        body.put("discountValue", 10.00);
        body.put("expirationDate", LocalDateTime.now().plusDays(30).toString());
        body.put("published", false);
        body.putAll(overrides);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void create_withMissingDescription_returns400WithStandardBody() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("description", "");

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void create_withCodeThatSanitizesToFewerThanSixChars_returns400() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("code", "AB-1#2");

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_withDiscountValueBelowMinimum_returns400() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("discountValue", 0.1);

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_withPastExpirationDate_returns400() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("expirationDate", LocalDateTime.now().minusDays(1).toString());

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_withDuplicateActiveCode_returns409() throws Exception {
        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(new LinkedHashMap<>())));

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(new LinkedHashMap<>())))
            .andExpect(status().isConflict());
    }

    @Test
    void findById_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/coupon/{id}", 999999L))
            .andExpect(status().isNotFound());
    }

    @Test
    void findById_withMalformedIdPathVariable_returns400() throws Exception {
        mockMvc.perform(get("/coupon/{id}", "not-a-number"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void delete_whenAlreadyDeleted_returns409() throws Exception {
        String created = mockMvc.perform(post("/coupon").contentType("application/json").content(payload(new LinkedHashMap<>())))
            .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/coupon/{id}", id));

        mockMvc.perform(delete("/coupon/{id}", id))
            .andExpect(status().isConflict());
    }

    @Test
    void delete_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/coupon/{id}", 999999L))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_thenSubsequentGetReturns404() throws Exception {
        String created = mockMvc.perform(post("/coupon").contentType("application/json").content(payload(new LinkedHashMap<>())))
            .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/coupon/{id}", id))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/coupon/{id}", id))
            .andExpect(status().isNotFound());
    }
}
