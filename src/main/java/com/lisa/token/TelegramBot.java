package com.lisa.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.lisa.user.Transaction;
import com.lisa.user.TransactionRepository;

public class TelegramBot extends TelegramLongPollingBot {
	@Autowired
	TransactionRepository tranRepo;

	Optional<Transaction> transaction;
	long longID;

	private enum BotState {
		NONE, WAITING_FOR_ID, WAITING_FOR_HASH
	}

	private BotState botState = BotState.NONE;

	@Override
	public String getBotUsername() {
		return "alert_account_bot"; // Thay thế bằng username của bot
	}

	@Override
	public String getBotToken() {
		return "6466424459:AAFwoILcZY15HKyZ7JYMfKkTVrpR9PTNvV4"; // Thay thế bằng API Token của bot
	}

	// Phương thức gửi tin nhắn
	public void sendMessageToChat(long chatId, String message) {
		SendMessage sendMessage = new SendMessage();
		sendMessage.setChatId(String.valueOf(chatId));
		sendMessage.setText(message);
		try {
			execute(sendMessage);
		} catch (TelegramApiException e) {
			System.out.println(e.getMessage());
		}
	}

	@Override
	public void onUpdateReceived(Update update) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			String messageText = update.getMessage().getText();
			long chatId = update.getMessage().getChatId();
			if (update.getMessage().getFrom().getUserName().equals("jasontran14")) {
				if (messageText.equals("/start")) { // Ví dụ: Khi người dùng gửi "/start"
					sendMenu(String.valueOf(chatId));
				} else if (messageText.equals("/withdrawal") || messageText.equals("Kiểm tra các lệnh rút")) {
					List<Transaction> transactionsIsNotAproved = tranRepo.findAllTransactionByStatus(0);
					if (transactionsIsNotAproved.size() == 0) {
						sendMessageToChat(chatId, "Không có lệnh rút cần duyệt!!!");
					} else {
						StringBuilder sb = new StringBuilder();
						for (Transaction tran : transactionsIsNotAproved) {
							sb.append("Lệnh rút ID#" + tran.getId() + " với số tiền: " + tran.getAmount() + "\n");
						}
						sendMessageToChat(chatId, sb.toString());
					}
				} else if (messageText.contains("approve") || messageText.equals("Duyệt lệnh rút")) {
					// Chuyển sang trạng thái chờ nhập ID
					botState = BotState.WAITING_FOR_ID;
					sendMessageToChat(chatId, "Nhập ID lệnh rút cần duyệt :");
				} else if (botState == BotState.WAITING_FOR_ID) {
					if (messageText.equals("Thoát")) {
				        botState = BotState.NONE; // Thoát khỏi trạng thái nhập ID
				        sendMenu(String.valueOf(chatId)); // Gửi lại menu chính cho người dùng
				        return;
				    }
					// Xử lý ID và chuyển sang trạng thái chờ nhập Hash
					String id = messageText;
					try {
						longID = Long.parseLong(id);
					} catch (Exception e) {
						sendMessageToChat(chatId, "Lệnh rút ID#" + longID + " không hợp lệ!");
						return;
					}

					transaction = tranRepo.findTransactionById(longID);
					if (transaction.isPresent()) {
						if (transaction.get().getStatus() == 0) {
							transaction.get().setApproveTime(String.valueOf(System.currentTimeMillis()));
							transaction.get().setStatus(1);
						} else {
							sendMessageToChat(chatId, "Lệnh rút ID#" + longID + " đã được xử lý, không thể thay đổi!");
							return;
						}
					} else {
						sendMessageToChat(chatId, "Lệnh rút ID#" + longID + " không tồn tại!");
						return;
					}

					sendMessageToChat(chatId, "Nhập mã giao dịch chuyển tại sàn Exness:");
					botState = BotState.WAITING_FOR_HASH;
				} else if (botState == BotState.WAITING_FOR_HASH) {
					if (messageText.equals("Thoát")) {
				        botState = BotState.NONE; // Thoát khỏi trạng thái nhập ID
				        sendMenu(String.valueOf(chatId)); // Gửi lại menu chính cho người dùng
				        return;
				    }
					
					// Xử lý Hash và duyệt lệnh rút
					String hash = messageText;
					transaction.get().setTransactionId(hash);
					tranRepo.save(transaction.get());
					sendMessageToChat(chatId, "Lệnh rút ID# " + longID + " đã được duyệt.");
					botState = BotState.NONE;
					return;
				} else if (messageText.equals("Thoát")) {
					botState = BotState.NONE;
				} else {
					sendMessageToChat(chatId, "Xin lỗi, chức năng bạn chọn không tồn tại!");
				}
			} else {
				sendMessageToChat(chatId, "Bạn không có quyền điều khiển bot trong group này!");
			}
		}
	}

	public void sendMenu(String chatId) {
	    SendMessage message = new SendMessage();
	    message.setChatId(chatId);
	    message.setText("Vui lòng chọn chức năng trên menu:");

	    // Tạo hàng cho nút thứ nhất
	    KeyboardRow row1 = new KeyboardRow();
	    row1.add("Kiểm tra các lệnh rút");

	    // Tạo hàng cho nút thứ hai
	    KeyboardRow row2 = new KeyboardRow();
	    row2.add("Duyệt lệnh rút");
	    
	 // Tạo hàng cho nút thứ ba
	    KeyboardRow row3 = new KeyboardRow();
	    row3.add("Thoát");

	    // Thêm cả hai hàng vào bàn phím
	    List<KeyboardRow> keyboard = new ArrayList<>();
	    keyboard.add(row1);
	    keyboard.add(row2);
	    keyboard.add(row3);

	    // Thêm hàng vào bàn phím
	    ReplyKeyboardMarkup replyMarkup = new ReplyKeyboardMarkup();
	    replyMarkup.setKeyboard(keyboard);
	    message.setReplyMarkup(replyMarkup);

	    try {
	        execute(message);
	    } catch (TelegramApiException e) {
	        e.printStackTrace();
	    }
	}

}
