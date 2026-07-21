package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.demo.model.persistence.Item;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
public class SareetaApplicationTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;

	private String createUser(String username, String password) throws Exception {
		String uniqueUsername = username + "-" + UUID.randomUUID().toString().substring(0, 8);
		var body = Map.of("username", uniqueUsername, "password", password, "confirmPassword", password);

		mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(uniqueUsername)).andExpect(jsonPath("$.password").doesNotExist());
		return uniqueUsername;
	}

	private String loginAndGetToken(String username, String password) throws Exception {
		var body = Map.of("username", username, "password", password);

		MvcResult result = mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn();

		String token = result.getResponse().getHeader("Authorization");
		assertThat(token).isNotBlank();
		assertThat(token).startsWith("Bearer ");
		return token;
	}

	@Test
	void items_requires_auth() throws Exception {
		mockMvc.perform(get("/api/item")).andExpect(status().isUnauthorized());
	}

	@Test
	void items_with_token_ok() throws Exception {
		String alice = createUser("alice", "password123");
		String token = loginAndGetToken(alice, "password123");

		mockMvc.perform(get("/api/item").header("Authorization", token)).andExpect(status().isOk());
	}

	@Test
	void cannot_access_other_user_profile() throws Exception {
		String alice = createUser("alice", "password123");
		String bob = createUser("bob", "password123");

		String token = loginAndGetToken(alice, "password123");

		mockMvc.perform(get("/api/user/" + bob).header("Authorization", token)).andExpect(status().isForbidden());
	}

	@Test
	void cart_add_requires_same_username() throws Exception {
		String alice = createUser("alice", "password123");
		String token = loginAndGetToken(alice, "password123");

		// obtener un item real
		String itemsJson = mockMvc.perform(get("/api/item").header("Authorization", token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		Item[] items = objectMapper.readValue(itemsJson, Item[].class);
		long itemId = items[0].getId();

		var bodyOk = Map.of("username", alice, "itemId", itemId, "quantity", 1);
		mockMvc.perform(post("/api/cart/addToCart").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bodyOk))).andExpect(status().isOk());

		var bodyNope = Map.of("username", "bob", "itemId", itemId, "quantity", 1);
		mockMvc.perform(post("/api/cart/addToCart").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bodyNope))).andExpect(status().isForbidden());
	}

	@Test
	void create_user_rejects_short_password_and_mismatch() throws Exception {
		var shortPassword = Map.of("username", "carol", "password", "short", "confirmPassword", "short");
		mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(shortPassword))).andExpect(status().isBadRequest());

		var mismatch = Map.of("username", "dave", "password", "password123", "confirmPassword", "password124");
		mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mismatch))).andExpect(status().isBadRequest());
	}

	@Test
	void create_user_rejects_duplicate_username() throws Exception {
		String erin = createUser("erin", "password123");
		var duplicate = Map.of("username", erin, "password", "password123", "confirmPassword", "password123");
		mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(duplicate))).andExpect(status().isConflict());
	}

	@Test
	void order_submit_works_for_authenticated_user() throws Exception {
		String frank = createUser("frank", "password123");
		String token = loginAndGetToken(frank, "password123");

		String itemsJson = mockMvc.perform(get("/api/item").header("Authorization", token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Item[] items = objectMapper.readValue(itemsJson, Item[].class);
		long itemId = items[0].getId();

		var addBody = Map.of("username", frank, "itemId", itemId, "quantity", 1);
		mockMvc.perform(post("/api/cart/addToCart").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addBody))).andExpect(status().isOk());

		mockMvc.perform(post("/api/order/submit/" + frank).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(frank));
	}

	@Test
	void invalid_jwt_is_rejected() throws Exception {
		mockMvc.perform(get("/api/item").header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized());
	}
}