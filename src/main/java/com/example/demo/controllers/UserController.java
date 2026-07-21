package com.example.demo.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.persistence.Cart;
import com.example.demo.model.persistence.User;
import com.example.demo.model.persistence.repositories.UserRepository;
import com.example.demo.model.requests.CreateUserRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@GetMapping("/id/{id}")
	public ResponseEntity<User> findById(@PathVariable Long id) {
		User user = userRepository.findById(id).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}

		if (!isSelf(user.getUsername()))
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

		return ResponseEntity.ok(user);
	}

	@GetMapping("/{username}")
	public ResponseEntity<User> findByUserName(@PathVariable String username) {
		User user = userRepository.findByUsername(username);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (!isSelf(user.getUsername()))
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

		return ResponseEntity.ok(user);
	}

	@PostMapping("/create")
	public ResponseEntity<User> createUser(@RequestBody CreateUserRequest createUserRequest) {
		if (createUserRequest.getUsername() == null || createUserRequest.getUsername().isBlank()) {
			log.warn("CreateUser rejected because username is missing");
			return ResponseEntity.badRequest().build();
		}
		if (createUserRequest.getPassword() == null || createUserRequest.getPassword().length() < 8) {
			log.warn("CreateUser rejected because password is too short for username={}",
					createUserRequest.getUsername());
			return ResponseEntity.badRequest().build();
		}
		if (!createUserRequest.getPassword().equals(createUserRequest.getConfirmPassword())) {
			log.warn("CreateUser rejected because confirmation password mismatch for username={}",
					createUserRequest.getUsername());
			return ResponseEntity.badRequest().build();
		}
		if (userRepository.findByUsername(createUserRequest.getUsername()) != null) {
			log.warn("CreateUser rejected because username already exists username={}",
					createUserRequest.getUsername());
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}

		User user = new User();
		user.setUsername(createUserRequest.getUsername());
		user.setPassword(passwordEncoder.encode(createUserRequest.getPassword()));

		Cart cart = new Cart();
		user.setCart(cart);
		cart.setUser(user);

		userRepository.save(user);
		log.info("CreateUser succeeded username={}", createUserRequest.getUsername());
		return ResponseEntity.ok(user);
	}

	private boolean isSelf(String username) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getName() != null && auth.getName().equals(username);
	}

}
