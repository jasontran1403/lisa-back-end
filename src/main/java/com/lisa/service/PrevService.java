package com.lisa.service;

import com.lisa.dto.PrevRequest;
import com.lisa.dto.PreviousMonthResponse;

public interface PrevService {
	PreviousMonthResponse findPrevByEmail(String email);
	void updatePrev(PrevRequest request);

}
