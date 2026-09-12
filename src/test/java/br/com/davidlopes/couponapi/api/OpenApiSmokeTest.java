package br.com.davidlopes.couponapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_isAvailable_andListsCouponPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths./coupon").exists());
    }

    @Test
    void apiDocs_documentsEachOperationWithSummaryAndResponseCodes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths./coupon.post.tags[0]").value("Coupon"))
            .andExpect(jsonPath("$.paths./coupon.post.summary").isNotEmpty())
            .andExpect(jsonPath("$.paths./coupon.post.description").isNotEmpty())
            .andExpect(jsonPath("$.paths./coupon.post.responses.201").exists())
            .andExpect(jsonPath("$.paths./coupon.post.responses.400").exists())
            .andExpect(jsonPath("$.paths./coupon.post.responses.409").exists())
            .andExpect(jsonPath("$.paths./coupon.get.summary").isNotEmpty())
            .andExpect(jsonPath("$.paths./coupon/{id}.get.summary").isNotEmpty())
            .andExpect(jsonPath("$.paths./coupon/{id}.get.responses.404").exists())
            .andExpect(jsonPath("$.paths./coupon/{id}.delete.summary").isNotEmpty())
            .andExpect(jsonPath("$.paths./coupon/{id}.delete.responses.204").exists())
            .andExpect(jsonPath("$.paths./coupon/{id}.delete.responses.409").exists());
    }

    @Test
    void swaggerUi_isAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }
}
