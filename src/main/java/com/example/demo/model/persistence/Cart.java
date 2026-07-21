package com.example.demo.model.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cart")
@Getter
@Setter
public class Cart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	@Column
	private Long id;

	@ManyToMany
	@JsonProperty
	private List<Item> items = new ArrayList<>();

	@OneToOne(mappedBy = "cart")
	@JsonProperty
	private User user;

	@Column
	@JsonProperty
	private BigDecimal total;

	public void addItem(Item item) {
		items.add(item);
		total = total == null ? BigDecimal.ZERO : total;
		total = total.add(item.getPrice());
	}

	public void removeItem(Item item) {
		items.remove(item);
		total = total == null ? BigDecimal.ZERO : total;
		total = total.subtract(item.getPrice());
	}

}
