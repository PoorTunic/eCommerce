package com.example.demo.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.persistence.User;
import com.example.demo.model.persistence.UserOrder;
import com.example.demo.model.persistence.repositories.OrderRepository;
import com.example.demo.model.persistence.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

	private final UserRepository userRepository;
	private final OrderRepository orderRepository;

	@PostMapping("/submit/{username}")
	public ResponseEntity<UserOrder> submit(@PathVariable String username) {
		if (!isSelf(username)) {
			log.warn("Order submission rejected because username does not match authenticated user username={}", username);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		User user = userRepository.findByUsername(username);
		if (user == null) {
			log.warn("Order submission failed because user not found username={}", username);
			return ResponseEntity.notFound().build();
		}
		UserOrder order = UserOrder.createFromCart(user.getCart());
		orderRepository.save(order);
		log.info("Order submission succeeded username={}", username);
		return ResponseEntity.ok(order);
	}

	@GetMapping("/history/{username}")
	public ResponseEntity<List<UserOrder>> getOrdersForUser(@PathVariable String username) {
		if (!isSelf(username)) {
			log.warn("Order history access rejected for username={}", username);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		User user = userRepository.findByUsername(username);
		if (user == null) {
			log.warn("Order history lookup failed because user not found username={}", username);
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(orderRepository.findByUser(user));
	}

	private boolean isSelf(String username) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getName() != null && auth.getName().equals(username);
	}
}
