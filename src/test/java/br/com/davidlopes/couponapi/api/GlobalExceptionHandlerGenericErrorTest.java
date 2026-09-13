package br.com.davidlopes.couponapi.api;

import br.com.davidlopes.couponapi.application.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerGenericErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @Test
    void findById_whenServiceThrowsUnexpectedException_returns500WithStandardBody() throws Exception {
        when(couponService.findById(any(UUID.class))).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/coupon/{id}", UUID.randomUUID()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500));
    }
}
