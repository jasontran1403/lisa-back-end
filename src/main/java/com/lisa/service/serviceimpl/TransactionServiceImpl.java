package com.lisa.service.serviceimpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lisa.service.TransactionService;
import com.lisa.user.Transaction;
import com.lisa.user.TransactionRepository;

@Service
public class TransactionServiceImpl implements TransactionService {
	@Autowired
	TransactionRepository tranRepo;

	@Override
	public double calWithdraw(String email) {
		// TODO Auto-generated method stub
		List<Transaction> transactions = tranRepo.findTransactionByWithdrawer(email);
		
		double total = 0;
		for (Transaction transaction : transactions) {
			total += transaction.getAmount();
		}
		return total;
	}

}
