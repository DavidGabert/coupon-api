package br.com.davidlopes.couponapi.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String validCreatePayload(String code) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("code", code);
            put("description", "10% off");
            put("discountValue", 10.00);
            put("expirationDate", LocalDateTime.now().plusDays(30).toString());
            put("published", false);
        }});
    }

    @Test
    void create_withValidPayload_returns201WithBody() throws Exception {
        mockMvc.perform(post("/coupon")
                .contentType("application/json")
                .content(validCreatePayload("AB12CD")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("AB12CD"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void findById_afterCreate_returns200WithSameCoupon() throws Exception {
        String created = mockMvc.perform(post("/coupon")
                .contentType("application/json")
                .content(validCreatePayload("AB12CD")))
            .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/coupon/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AB12CD"));
    }

    @Test
    void findAll_returnsAllActiveCoupons() throws Exception {
        mockMvc.perform(post("/coupon").contentType("application/json").content(validCreatePayload("AB12CD")));
        mockMvc.perform(post("/coupon").contentType("application/json").content(validCreatePayload("EF34GH")));

        mockMvc.perform(get("/coupon"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void delete_afterCreate_returns204() throws Exception {
        String created = mockMvc.perform(post("/coupon")
                .contentType("application/json")
                .content(validCreatePayload("AB12CD")))
            .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/coupon/{id}", id))
            .andExpect(status().isNoContent());
    }
}
