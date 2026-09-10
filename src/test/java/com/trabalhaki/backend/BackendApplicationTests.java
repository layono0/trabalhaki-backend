package com.trabalhaki.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
		org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
		System.out.println("TEST_BCRYPT_NEW_HASH: " + encoder.encode("123456"));
		System.out.println("TEST_BCRYPT_MATCHES_OLD: " + encoder.matches("123456", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
	}

}
