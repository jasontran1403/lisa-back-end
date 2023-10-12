package com.lisa.service;


import com.lisa.dto.EmailDto;

import jakarta.mail.MessagingException;

public interface EmailService {
	void send(EmailDto mail) throws MessagingException;
}
