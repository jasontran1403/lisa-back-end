package com.lisa.service.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lisa.dto.MessageRequest;
import com.lisa.service.MessageService;
import com.lisa.user.Message;
import com.lisa.user.MessageRepository;
import com.lisa.user.User;
import com.lisa.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService{
	private final UserRepository userRepo;
	private final MessageRepository messRepo;

	@Override
	public Message saveMessage(MessageRequest messageRequest) {
		// TODO Auto-generated method stub
		User user = userRepo.findByEmail(messageRequest.getEmail()).get();
		Message message = new Message();
		message.setTime(System.currentTimeMillis() / 1000);
		message.setMessage(messageRequest.getMessage());
		message.setRead(false);
		message.setUser(user);
		return messRepo.save(message);
	}

	@Override
	public List<Message> findMessagesByEmail(String email) {
		// TODO Auto-generated method stub
		User user = userRepo.findByEmail(email).get();
		List<Message> result = messRepo.findMessagesByUser(user.getId());
		return result.size() > 0 ? result : new ArrayList<>();
	}

	@Override
	public void toggleMessageStatus(long id) {
		// TODO Auto-generated method stub
		Message message = messRepo.findById(id).get();
		message.setRead(false);
		messRepo.save(message);
	}

}
