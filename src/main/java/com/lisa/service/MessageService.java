package com.lisa.service;

import java.util.List;

import com.lisa.dto.MessageRequest;
import com.lisa.user.Message;

public interface MessageService {
	Message saveMessage(MessageRequest message);
	List<Message> findMessagesByEmail(String email);
	void toggleMessageStatus(long id);
}
