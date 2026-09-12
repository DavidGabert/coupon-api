package br.com.davidlopes.couponapi.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void create_withDescriptionLongerThanColumnLimit_returns400NotConflict() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("description", "x".repeat(256));

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_withDiscountValueExceedingColumnPrecision_returns400NotConflict() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("discountValue", new BigDecimal("99999999999999999999.99"));

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_withMoreThanTwoDecimalPlaces_returnsTheValueThatWasActuallyPersisted() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("discountValue", new BigDecimal("0.50000001"));

        String created = mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        BigDecimal createdValue = objectMapper.readTree(created).get("discountValue").decimalValue();

        String fetched = mockMvc.perform(get("/coupon/{id}", id))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        BigDecimal fetchedValue = objectMapper.readTree(fetched).get("discountValue").decimalValue();

        assertThat(createdValue).isEqualByComparingTo("0.50");
        assertThat(createdValue).isEqualByComparingTo(fetchedValue);
    }

    @Test
    void create_withWrongJsonTypeForDiscountValue_returns400WithStandardBody() throws Exception {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("discountValue", "abc");

        mockMvc.perform(post("/coupon").contentType("application/json").content(payload(overrides)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void create_withMalformedJson_returns400WithStandardBody() throws Exception {
        mockMvc.perform(post("/coupon").contentType("application/json").content("{\"code\": \"AB12CD\", "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void create_withUnsupportedContentType_returns415WithStandardBody() throws Exception {
        mockMvc.perform(post("/coupon").contentType("text/plain").content(payload(new LinkedHashMap<>())))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedHttpMethodOnCouponResource_returns405WithStandardBody() throws Exception {
        mockMvc.perform(put("/coupon/{id}", 1L).contentType("application/json").content(payload(new LinkedHashMap<>())))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownSubPath_returns404WithStandardBody() throws Exception {
        mockMvc.perform(get("/coupon/{id}/nope", 1L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.timestamp").exists());
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
