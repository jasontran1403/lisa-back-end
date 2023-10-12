package com.lisa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviousMonthResponse {
	private double balance;
	private double commission;
	private double transaction;
}
