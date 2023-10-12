package com.lisa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InfoResponse {
	private String firstName;
	private String lastName;
	private double balance;
	private double commission;
	private double withdraw;
}
