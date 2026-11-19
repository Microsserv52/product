package Microsserv52.product;

import java.util.UUID;

import Microsserv52.product.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.cache.enabled=true",
    "spring.data.redis.url=redis://127.0.0.1:1/0",
    "spring.data.redis.connect-timeout=100ms",
    "spring.data.redis.timeout=100ms",
    "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class ProductCacheOutageTest {
    private static final UUID ACCOUNT = UUID.fromString("0195ae95-5be7-7dd3-b35d-7a7d87c404fb");
    private static final String ADMIN = "Bearer " + TestTokens.adminToken(ACCOUNT);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redisOutageDegradesToTheDatabase() throws Exception {
        mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cheese","price":0.62,"unit":"slice"}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk());
    }
}
