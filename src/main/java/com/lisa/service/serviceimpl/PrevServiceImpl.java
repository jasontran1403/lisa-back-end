package com.lisa.service.serviceimpl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.lisa.dto.PrevRequest;
import com.lisa.dto.PreviousMonthResponse;
import com.lisa.service.PrevService;
import com.lisa.user.Prev;
import com.lisa.user.PrevRepository;
import com.lisa.user.User;
import com.lisa.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrevServiceImpl implements PrevService{
	private final UserRepository userRepo;
	private final PrevRepository prevRepo;
	
	@Override
	public PreviousMonthResponse findPrevByEmail(String email) {
		// TODO Auto-generated method stub
		User user = userRepo.findByEmail(email).get();
		Optional<Prev> prev = prevRepo.getPrevByUser(user);
		if (prev.isEmpty()) {
			return new PreviousMonthResponse();
		}
		PreviousMonthResponse result = new PreviousMonthResponse();
		result.setBalance(prev.get().getBalance());
		result.setCommission(prev.get().getCommission());
		result.setTransaction(prev.get().getTransaction());
		return result;
	}

	@Override
	public void updatePrev(PrevRequest request) {
		// TODO Auto-generated method stub
		Prev prev = new Prev();
		User user = userRepo.findByEmail(request.getEmail()).get();
		prev.setUser(user);
		prev.setBalance(request.getBalance());
		prev.setCommission(request.getCommission());
		prev.setTransaction(request.getTransaction());
		prevRepo.save(prev);
		
	}

}
