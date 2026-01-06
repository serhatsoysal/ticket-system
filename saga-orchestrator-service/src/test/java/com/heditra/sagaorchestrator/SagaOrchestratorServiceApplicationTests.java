package com.heditra.sagaorchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestConfig.class)
class SagaOrchestratorServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

