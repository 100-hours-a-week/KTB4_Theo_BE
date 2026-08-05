package com.theo.community_api;

import com.theo.community_api.image.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommunityApiApplicationTests {

	@MockitoBean
	private ImageStorage imageStorage;

	@Test
	void contextLoads() {
	}

}
