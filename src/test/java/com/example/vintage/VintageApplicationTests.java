package com.example.vintage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Use in-memory DB to avoid locking the file-based H2 during CI/tests
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update"
})
class VintageApplicationTests {

    @Test
    void contextLoads() {
    }

}
