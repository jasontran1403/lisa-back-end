package com.lisa.demo;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.poi.openxml4j.exceptions.PartAlreadyExistsException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.zxing.WriterException;
import com.lisa.auth.AuthenticationService;
import com.lisa.auth.ChangePasswordRequest;
import com.lisa.auth.ExnessResponse;
import com.lisa.auth.GetCodeRequest;
import com.lisa.auth.HistoryResponse;
import com.lisa.auth.TransactionResponse;
import com.lisa.auth.TwoFARequest;
import com.lisa.auth.UpdateExnessRequest;
import com.lisa.auth.UpdateRefResponse;
import com.lisa.dto.InfoResponse;
import com.lisa.dto.NetworkDto;
import com.lisa.dto.NetworkResponse;
import com.lisa.dto.PreviousMonthResponse;
import com.lisa.dto.UpdateInfoRequest;
import com.lisa.service.HistoryService;
import com.lisa.service.MessageService;
import com.lisa.service.PrevService;
import com.lisa.service.TransactionService;
import com.lisa.token.TelegramBot;
import com.lisa.token.WithdrawRequest;
import com.lisa.user.Exness;
import com.lisa.user.ExnessRepository;
import com.lisa.user.ExnessTransaction;
import com.lisa.user.ExnessTransactionRepository;
import com.lisa.user.History;
import com.lisa.user.Message;
import com.lisa.user.Transaction;
import com.lisa.user.TransactionRepository;
import com.lisa.user.User;
import com.lisa.user.UserRepository;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/secured")
@CrossOrigin("*")
@Hidden
@RequiredArgsConstructor
public class DemoController {
	private final UserRepository userRepo;
	private final ExnessRepository exRepo;
	private final ExnessTransactionRepository exTranRepo;
	private final MessageService messService;
	private final AuthenticationService service;
	private final PrevService prevService;
	private final TransactionRepository tranRepo;
	private final TransactionService tranService;
	private final SecretGenerator secretGenerator;
	private final PasswordEncoder passwordEncoder;
	private final QrDataFactory qrDataFactory;
	private final QrGenerator qrGenerator;
	private final TelegramBot tele = new TelegramBot();

	@Autowired
	HistoryService hisService;

	@GetMapping("/test")
	public ResponseEntity<List<User>> test() {
		return ResponseEntity.ok(userRepo.findAll());
	}

	@GetMapping("/get-prev-data/{email}")
	public ResponseEntity<PreviousMonthResponse> getPreviousMonthData(@PathVariable("email") String email) {
		PreviousMonthResponse result = prevService.findPrevByEmail(email);
		return ResponseEntity.ok(result);
	}

	@PostMapping("/edit-info")
	public ResponseEntity<String> editInfo(@RequestBody UpdateInfoRequest request) {
		service.editInfo(request);
		return ResponseEntity.ok("OK");
	}

	@GetMapping("/showQR/{email}")
	public List<String> generate2FA(@PathVariable("email") String email)
			throws QrGenerationException, WriterException, IOException, CodeGenerationException {
		Optional<User> user = userRepo.findByEmail(email);
		QrData data = qrDataFactory.newBuilder().label(user.get().getEmail()).secret(user.get().getSecret())
				.issuer("Something Application").period(30).build();

		String qrCodeImage = getDataUriForImage(qrGenerator.generate(data), qrGenerator.getImageMimeType());
		List<String> info2FA = new ArrayList<>();
		String isEnabled = "";
		if (user.get().isMfaEnabled()) {
			isEnabled = "true";
		} else {
			isEnabled = "false";
		}
		info2FA.add(isEnabled);
		info2FA.add(qrCodeImage);

		return info2FA;
	}

	@GetMapping("/get-account-info/{email}")
	public ResponseEntity<InfoResponse> getAccountInfo(@PathVariable("email") String email) {
		InfoResponse infoResponse = new InfoResponse();
		User user = userRepo.findByEmail(email).get();
		infoResponse.setBalance(user.getBalance());
		infoResponse.setFirstName(user.getFirstname());
		infoResponse.setLastName(user.getLastname());
		infoResponse.setCommission(hisService.calculateAllIB(user.getEmail()));
		infoResponse.setWithdraw(tranService.calWithdraw(user.getEmail()));
		return ResponseEntity.ok(infoResponse);
	}

	@PostMapping("/enable")
	public String enabled(@RequestBody TwoFARequest request) {
		Optional<User> user = userRepo.findByEmail(request.getEmail());
		TimeProvider timeProvider = new SystemTimeProvider();
		CodeGenerator codeGenerator = new DefaultCodeGenerator();
		DefaultCodeVerifier verify = new DefaultCodeVerifier(codeGenerator, timeProvider);
		verify.setAllowedTimePeriodDiscrepancy(0);

		if (verify.isValidCode(user.get().getSecret(), request.getCode())) {
			user.get().setMfaEnabled(true);
			userRepo.save(user.get());
			return "Enabled Success";
		} else {
			return "Enabled Failed";
		}
	}

	@PostMapping("/disable")
	public String disabled(@RequestBody TwoFARequest request) {
		Optional<User> user = userRepo.findByEmail(request.getEmail());
		TimeProvider timeProvider = new SystemTimeProvider();
		CodeGenerator codeGenerator = new DefaultCodeGenerator();
		DefaultCodeVerifier verify = new DefaultCodeVerifier(codeGenerator, timeProvider);
		verify.setAllowedTimePeriodDiscrepancy(0);

		if (verify.isValidCode(user.get().getSecret(), request.getCode())) {
			// xóa secret 2fa
			String secret = secretGenerator.generate();

			user.get().setMfaEnabled(false);
			user.get().setSecret(secret);
			userRepo.save(user.get());
			return "Disabled Success";
		} else {
			return "Disabled Failed";
		}

	}

	@PostMapping("/update-exness")
	public ResponseEntity<UpdateRefResponse> updateExness(@RequestBody UpdateExnessRequest request) {
		return ResponseEntity.ok(service.updateExness(request.getEmail(), request.getExness(), request.getType()));
	}

	@GetMapping("/get-exness/{email}")
	public ResponseEntity<List<ExnessResponse>> getExness(@PathVariable("email") String email) {
		return ResponseEntity.ok(service.getExness(email));
	}

	@GetMapping("/getHistory/{email}")
	public ResponseEntity<List<HistoryResponse>> getHistoryByEmail(@PathVariable("email") String email) {
		List<History> listHistories = hisService.findHistoryByReceiver(email);
		List<HistoryResponse> listHistoryResponse = new ArrayList<>();
		for (History history : listHistories) {
			HistoryResponse historyResponse = new HistoryResponse();
			historyResponse.setSender(history.getSender());
			historyResponse.setReceiver(history.getReceiver());
			historyResponse.setAmount(history.getAmount());
			historyResponse.setMessage(history.getMessage());
			historyResponse.setTime(history.getTime());
			historyResponse.setTransaction(history.getTransaction());
			listHistoryResponse.add(historyResponse);
		}

		return ResponseEntity.ok(listHistoryResponse);
	}

	@GetMapping("/getTransaction/{email}")
	public ResponseEntity<List<TransactionResponse>> getTransactionByEmail(@PathVariable("email") String email) {
		List<Transaction> listTransactions = tranRepo.findTransactionByWithdrawer(email);
		List<TransactionResponse> listTransactionResponse = new ArrayList<>();
		for (Transaction transaction : listTransactions) {
			TransactionResponse transactionResponse = new TransactionResponse();
			transactionResponse.setId(transaction.getId());
			transactionResponse.setAmount(transaction.getAmount());
			transactionResponse.setTime(transaction.getTime());
			transactionResponse.setTransaction(transaction.getTransactionId());
			transactionResponse.setStatus(transaction.getStatus());
			listTransactionResponse.add(transactionResponse);
		}

		return ResponseEntity.ok(listTransactionResponse);
	}

	@GetMapping("/getIbHistory/{email}&from={from}&to={to}")
	public ResponseEntity<List<HistoryResponse>> getIBHistoryByEmail(@PathVariable("email") String email,
			@PathVariable("from") long from, @PathVariable("to") long to) {
		List<History> listHistories = hisService.findHistoryByReceiverAndTimeRange(email, from, to);
		List<HistoryResponse> listHistoryResponse = new ArrayList<>();

		// Sắp xếp danh sách theo ngày giảm dần
		listHistories.sort(Comparator.comparing(History::getTime));

		LocalDate currentDate = null;
		double currentTotalAmount = 0.0;

		for (History history : listHistories) {
			long timeUnix = Long.parseLong(history.getTime());
			Timestamp timestamp = new Timestamp(timeUnix * 1000); // Chuyển đổi thành miligisecond

			// Chuyển đổi java.sql.Timestamp thành java.util.Date
			java.util.Date date = new java.util.Date(timestamp.getTime());

			// Chuyển đổi java.util.Date thành LocalDate
			LocalDate historyDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

			if (currentDate == null) {
				currentDate = historyDate;
				currentTotalAmount = history.getAmount();
			} else if (currentDate.equals(historyDate)) {
				// Cùng ngày, cộng dồn số lượng
				currentTotalAmount += history.getAmount();
			} else {
				// Ngày khác, thêm lịch sử cuối cùng vào danh sách
				HistoryResponse historyResponse = new HistoryResponse();
				historyResponse.setSender(history.getSender());
				historyResponse.setReceiver(history.getReceiver());
				historyResponse.setAmount(currentTotalAmount);
				historyResponse.setMessage(history.getMessage());
				historyResponse.setTime(Timestamp.valueOf(currentDate.atStartOfDay()).toString());
				historyResponse.setTransaction(history.getTransaction());
				listHistoryResponse.add(historyResponse);

				// Cập nhật currentDate và currentTotalAmount
				currentDate = historyDate;
				currentTotalAmount = history.getAmount();
			}
		}

		// Thêm lịch sử cuối cùng vào danh sách
		if (currentDate != null) {
			HistoryResponse historyResponse = new HistoryResponse();
			historyResponse.setSender(listHistories.get(0).getSender());
			historyResponse.setReceiver(listHistories.get(0).getReceiver());
			historyResponse.setAmount(currentTotalAmount);
			historyResponse.setMessage(listHistories.get(0).getMessage());
			historyResponse.setTime(Timestamp.valueOf(currentDate.atStartOfDay()).toString());
			historyResponse.setTransaction(listHistories.get(0).getTransaction());
			listHistoryResponse.add(historyResponse);
		}
//	    Collections.reverse(listHistoryResponse);
		return ResponseEntity.ok(listHistoryResponse);
	}

	@GetMapping("/getBalance/{email}")
	public ResponseEntity<Double> getBalance(@PathVariable("email") String email) {
		Optional<User> user = userRepo.findByEmail(email);

		return ResponseEntity.ok(user.get().getBalance());
	}

	@PostMapping("/change-password")
	public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
		Optional<User> user = userRepo.findByEmail(request.getEmail());
		if (user.isEmpty()) {
			return ResponseEntity.ok("Tài khoản không tồn tại!");
		}

		TimeProvider timeProvider = new SystemTimeProvider();
		CodeGenerator codeGenerator = new DefaultCodeGenerator();
		DefaultCodeVerifier verify = new DefaultCodeVerifier(codeGenerator, timeProvider);
		verify.setAllowedTimePeriodDiscrepancy(0);

		if (verify.isValidCode(user.get().getSecret(), request.getCode())) {
			user.get().setPassword(passwordEncoder.encode(request.getPassword()));
			userRepo.save(user.get());
			return ResponseEntity.ok("Thay đổi mật khẩu thành công!");
		} else {
			return ResponseEntity.ok("Mã 2FA không chính xác!");
		}
	}

	@PostMapping("/withdraw-ib")
	public ResponseEntity<String> withdrawIB(@RequestBody WithdrawRequest request) {
		String message = "[Withdraw] " + request.getEmail() + " rút " + request.getAmount();
		User user = userRepo.findByEmail(request.getEmail()).get();
		if (request.getAmount() > user.getBalance() - 0.1) {
			return ResponseEntity.ok("Không đủ số dư để rút, luôn phải chừa lại 1 cent ~ $0.1");
		}

		TimeProvider timeProvider = new SystemTimeProvider();
		CodeGenerator codeGenerator = new DefaultCodeGenerator();
		DefaultCodeVerifier verify = new DefaultCodeVerifier(codeGenerator, timeProvider);
		verify.setAllowedTimePeriodDiscrepancy(0);

		if (verify.isValidCode(user.getSecret(), request.getCode())) {
			user.setBalance(user.getBalance() - request.getAmount());
			userRepo.save(user);

			Transaction transaction = new Transaction();
			transaction.setTime(String.valueOf(System.currentTimeMillis() / 1000));
			transaction.setStatus(0);
			transaction.setWithdrawer(request.getEmail());
			transaction.setAmount(request.getAmount());
			tranRepo.save(transaction);

			tele.sendMessageToChat(Long.parseLong("-1001804531952"), message);
			return ResponseEntity.ok("Rút thành công!");
		} else {
			return ResponseEntity.ok("Mã 2FA không chính xác!");
		}
	}

	@SuppressWarnings("resource")
	@PostMapping("/ib")
	@PreAuthorize("hasAuthority('admin:create')")
	public ResponseEntity<HashMap<Integer, String>> payIB(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			//return "Bạn chưa đính kèm file dữ liệu";
		}

		HashMap<Integer, String> data = new HashMap<>();
		InputStream inputStream = null;
		Workbook workbook = null;

		try {
			// Đọc tệp Excel
			inputStream = file.getInputStream();
			workbook = new XSSFWorkbook(inputStream);
			Sheet sheet = workbook.getSheetAt(0); // Chọn sheet cần đọc dữ liệu

			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				//return "File không đúng định dạng (dữ liệu trống)";
			} else if (headerRow.getPhysicalNumberOfCells() != 16) {
				//return "File không đúng định dạng (16 cột)";
			}

			String idHeader = getCellValueAsString(headerRow.getCell(0));
			String rewardHeader = getCellValueAsString(headerRow.getCell(9));
			String exnessIdHeader = getCellValueAsString(headerRow.getCell(14));

			if (!"id".equals(idHeader)) {
				//return "File không đúng định dạng (cột thứ 1 không phải là id)";
			}

			if (!"reward".equals(rewardHeader)) {
				//return "File không đúng định dạng (cột thứ 10 không phải là reward)";
			}

			if (!"client_account".equals(exnessIdHeader)) {
				//return "File không đúng định dạng (cột thứ 15 không phải là client_account - Exness ID)";
			}

			// Lặp qua từng dòng (bắt đầu từ dòng thứ 2, do dòng đầu tiên là tiêu đề)
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);

				// Đọc giá trị từ cột thứ 4, 5 và 7
				Cell cellTransaction = row.getCell(0);
				Cell cellIB = row.getCell(9); // Cột thứ 5 (index 4)
				Cell cellExnessId = row.getCell(14); // Cột thứ 7 (index 6)

				// Kiểm tra xem cell4, cell5 và cell7 có dữ liệu không
				if (cellTransaction != null && cellIB != null && cellExnessId != null) {
					String ibTransaction = getCellValueAsString(cellTransaction);
					String ibReward = getCellValueAsString(cellIB);
					String exnessIdValue = getCellValueAsString(cellExnessId);
					if (exnessIdValue.contains("E") || ibTransaction.contains("E")) {
						// Xử lý giá trị số thập phân với dấu phẩy
						double exnessIdDouble = Double.parseDouble(exnessIdValue);
						long exnessIdLong = (long) exnessIdDouble;
						exnessIdValue = String.valueOf(exnessIdLong);

						double exnessTransactionDouble = Double.parseDouble(ibTransaction);
						long exnessTransactionLong = (long) exnessTransactionDouble;
						ibTransaction = String.valueOf(exnessTransactionLong);
					}
					String value = ibTransaction + "-" + ibReward + "-" + exnessIdValue;
					data.put(rowIndex, value);

				}
			}
			workbook.close();
			inputStream.close();

		} catch (IOException e) {
			e.printStackTrace();
			//return "Lỗi khi đọc file!";
		} catch (PartAlreadyExistsException pae) {
			System.out.println(pae);
			//return "File ở chế độ Protected!";
		}

		double[] totalAmount = { 0.0, 0.0, 0.0 };

		List<History> toAdmin = new ArrayList<>();
		List<History> toUser = new ArrayList<>();
		List<History> toLisa = new ArrayList<>();
		List<String> listExness = new ArrayList<>();

		StringBuilder sb = new StringBuilder();
//		data.forEach((key, value) -> {
//			int firstDashIndex = value.indexOf('-');
//			int secondDashIndex = value.indexOf('-', firstDashIndex + 1);
//			String exnessTransaction = value.substring(0, firstDashIndex);
//			String exnessId = value.substring(secondDashIndex + 1, value.length());
//			double amount = Double.parseDouble(value.substring(firstDashIndex + 1, secondDashIndex));
//			double originalAmountPayToNetwork = amount * 0.8;
//			double remainingAmountPayToNetwork = originalAmountPayToNetwork;
//			double amountToAdmin = amount - originalAmountPayToNetwork;
//
//			// Kiem tra khoan hoa hong do da tra hay chua
//			Optional<ExnessTransaction> exTran = exTranRepo.findByTransactionExness(exnessTransaction);
//			if (exTran.isPresent()) {
//				sb.append(exnessTransaction + " đã được chi trả.\n");
//			} else {
//				totalAmount[0] += amount;
//				totalAmount[1] += amountToAdmin;
//				// Chi cho system 20% IB
//				History historyToAdmin = new History();
//				User userAdmin = userRepo.findByEmail("admin@gmail.com").get();
//				historyToAdmin.setAmount(amountToAdmin);
//				historyToAdmin.setReceiver("admin@gmail.com");
//				historyToAdmin.setSender(exnessId);
//				historyToAdmin.setTransaction(exnessTransaction);
//				historyToAdmin.setTime(String.valueOf(System.currentTimeMillis() / 1000));
//				historyToAdmin.setMessage("20% từ số IB=" + amount + " của ExnessID=" + exnessId);
//				historyToAdmin.setUser(userAdmin);
//				toAdmin.add(historyToAdmin);
////				hisService.saveHistory(historyToAdmin);
////
////				userAdmin.setBalance(userAdmin.getBalance() + amountToAdmin);
////				userRepo.save(userAdmin);
//
//				HashMap<Integer, String> listToPayIB = getNetWorkToLisa(exnessId);
//				System.out.println(listToPayIB.size());
//				for (HashMap.Entry<Integer, String> entry : listToPayIB.entrySet()) {
//					String recipientEmail = entry.getValue();
//					double amountToPay = 0.0;
//
//					if (recipientEmail.equals("lisa@gmail.com")) {
//						// Nếu người nhận là lisa@gmail, gửi toàn bộ số remainingAmountPayToNetwork (số
//						// IB chia còn lại khi gặp lisa@gmail.com) cho họ
//						amountToPay = remainingAmountPayToNetwork;
//						History historyToLisa = new History();
//						User userLisa = userRepo.findByEmail("lisa@gmail.com").get();
//						historyToLisa.setAmount(amountToPay);
//						historyToLisa.setReceiver(userLisa.getEmail());
//						historyToLisa.setSender(exnessId);
//						historyToLisa.setTransaction(exnessTransaction);
//						historyToLisa.setTime(String.valueOf(System.currentTimeMillis() / 1000));
//						historyToLisa.setMessage("Tìm thấy Lisa, chi hết số IB=" + amount + " còn lại của ExnessID=" + exnessId);
//						historyToLisa.setUser(userLisa);
//
//						toLisa.add(historyToLisa);
////						hisService.saveHistory(historyToLisa);
////
////						userLisa.setBalance(userLisa.getBalance() + amountToPay);
////						userRepo.save(userLisa);
//						totalAmount[2] += amountToPay;
//						remainingAmountPayToNetwork -= amountToPay;
//						break; // Dừng vòng lặp vì đã gửi hết số tiền
//					} else {
//						if (recipientEmail.equals("admin@gmail.com")) {
//							// Không chia cho tài khoản
//							continue;
//						} else {
//							// Ngược lại, gửi 50% của remainingAmountPayToNetwork cho người nhận
//							amountToPay = remainingAmountPayToNetwork / 2;
//							History historyToSystem = new History();
//							User userInSystem = userRepo.findByEmail(recipientEmail).get();
//							historyToSystem.setAmount(amountToPay);
//							historyToSystem.setReceiver(userInSystem.getEmail());
//							historyToSystem.setSender(exnessId);
//							historyToSystem.setTransaction(exnessTransaction);
//							historyToSystem.setTime(String.valueOf(System.currentTimeMillis() / 1000));
//							historyToSystem.setMessage("Hoa hồng từ khoản IB=" + amount + " của ExnessID=" + exnessId);
//							historyToSystem.setUser(userInSystem);
//							toUser.add(historyToSystem);
////							hisService.saveHistory(historyToSystem);
////
////							userInSystem.setBalance(userInSystem.getBalance() + amountToPay);
////							userRepo.save(userInSystem);
//							totalAmount[2] += amountToPay;
//							remainingAmountPayToNetwork -= amountToPay; // Giảm số tiền còn lại
//						}
//					}
//				}
//				if (remainingAmountPayToNetwork > 0) {
//					History historyToLisa = new History();
//					User userLisa = userRepo.findByEmail("lisa@gmail.com").get();
//					historyToLisa.setAmount(remainingAmountPayToNetwork);
//					historyToLisa.setReceiver(userLisa.getEmail());
//					historyToLisa.setSender(exnessId);
//					historyToLisa.setTransaction(exnessTransaction);
//					historyToLisa.setTime(String.valueOf(System.currentTimeMillis() / 1000));
//					historyToLisa.setMessage("Số còn lại từ khoản IB=" + amount + " của ExnessID=" + exnessId);
//					historyToLisa.setUser(userLisa);
//
//					toLisa.add(historyToLisa);
//				}
//
//				listExness.add(exnessTransaction);
//			}
//		});
//
//		tele.sendMessageToChat(Long.parseLong("-1001804531952"),
//				"Tổng số tiền IB là: " + totalAmount[0] + "\nTổng số tiền chi về Admin là: " + totalAmount[1]
//						+ "\nTổng số tiền chi cho hệ thống là: " + totalAmount[2]);
//		if (!sb.isEmpty()) {
//			tele.sendMessageToChat(Long.parseLong("-1001804531952"), sb.toString());
//		}
//
//		Thread thread1 = new Thread() {
//			public void run() {
//				for (String item : listExness) {
//					ExnessTransaction exnessTransactionFromExcel = new ExnessTransaction();
//					exnessTransactionFromExcel.setTime(String.valueOf(System.currentTimeMillis()));
//					exnessTransactionFromExcel.setTransactionExness(item);
//					exTranRepo.save(exnessTransactionFromExcel);
//				}
//			}
//		};
//
//		Thread thread2 = new Thread() {
//			public void run() {
//				for (History item : toAdmin) {
//					hisService.saveHistory(item);
//					User user = userRepo.findByEmail(item.getReceiver()).get();
//					user.setBalance(user.getBalance() + item.getAmount());
//					userRepo.save(user);
//				}
//			}
//		};
//
//		Thread thread3 = new Thread() {
//			public void run() {
//				for (History item : toLisa) {
//					hisService.saveHistory(item);
//					User user = userRepo.findByEmail(item.getReceiver()).get();
//					user.setBalance(user.getBalance() + item.getAmount());
//					userRepo.save(user);
//				}
//			}
//		};
//
//		Thread thread4 = new Thread() {
//			public void run() {
//				for (History item : toUser) {
//					hisService.saveHistory(item);
//					User user = userRepo.findByEmail(item.getReceiver()).get();
//					user.setBalance(user.getBalance() + item.getAmount());
//					userRepo.save(user);
//				}
//			}
//		};
//
//		thread1.start();
//		thread2.start();
//		thread3.start();
//		thread4.start();
		
		System.out.println(data);

		return ResponseEntity.ok(data);
	}

	@GetMapping("/get-message/{email}")
	public ResponseEntity<List<Message>> getMessage(@PathVariable("email") String email) {
		List<Message> listMessages = messService.findMessagesByEmail(email);
		return ResponseEntity.ok(listMessages);
	}

	@GetMapping("/toggle-message/id={id}")
	public ResponseEntity<String> toggleMessage(@PathVariable("id") long id) {
		messService.toggleMessageStatus(id);
		return ResponseEntity.ok("OK");
	}

	@PostMapping("/get-code")
	public ResponseEntity<String> getInfo(@RequestBody GetCodeRequest request) {
		return ResponseEntity.ok(service.getRefferal(request.getEmail()));
	}

	@GetMapping("/get-network/{email}")
	public ResponseEntity<HashMap<Integer, List<NetworkResponse>>> getNetwork(@PathVariable("email") String email) {
		return ResponseEntity.ok(getNetworkFromUser(email));
	}

	@GetMapping("/getNetwork/{email}")
	public ResponseEntity<List<NetworkDto>> getNetworkLevel(@PathVariable("email") String email) {
		int level = 1;
		int root = 1;
		List<NetworkDto> network = new ArrayList<>();
		getUserNetwork(email, level, root, network);

		Collections.sort(network);
		return ResponseEntity.ok(network);
	}

	private void getUserNetwork(String email, int desiredLevel, int currentLevel, List<NetworkDto> network) {
		if (currentLevel <= desiredLevel) {
			List<User> users = userRepo.findByRefferal(email);
			if (users.isEmpty()) {
				return;
			}

			for (User user : users) {
				network.add(new NetworkDto(user.getEmail(), email, currentLevel));
				getUserNetwork(user.getEmail(), desiredLevel, currentLevel + 1, network);
			}
		}
	}

	private HashMap<Integer, List<NetworkResponse>> getNetworkFromUser(String email) {
		HashMap<Integer, List<NetworkResponse>> result = new HashMap<>();
		List<User> listUser = userRepo.findAllByRefferal(email);
		List<NetworkResponse> listNetwork = new ArrayList<>();

		for (User user : listUser) {
			NetworkResponse item = new NetworkResponse();
			item.setEmail(user.getEmail());
			item.setRefferal(user.getRefferal());
			listNetwork.add(item);
		}
		result.put(1, listNetwork);
		return result;
	}

	private HashMap<Integer, String> getNetWorkToLisa(String exness) {
		HashMap<Integer, String> listNetWorks = new HashMap<>();
		try {
			Optional<Exness> exnessF0 = exRepo.findByExness(exness);
			int level = 1;

			String userF1 = exnessF0.get().getUser().getRefferal();
			listNetWorks.put(level, userF1);
			level++;
			String userPointer = userF1;

			do {
				Optional<User> user = userRepo.findByEmail(userPointer);
				if (user.isEmpty()) {
					break;
				}
				if (!user.get().getRefferal().equals("")) {
					listNetWorks.put(level, user.get().getRefferal());
				}

				userPointer = user.get().getRefferal();
				level++;
			} while (!userPointer.equals("lisa@gmail.com") && level <= 5);
		} catch (Exception e) {
			return new HashMap<>();
		}

		return listNetWorks;
	}

	private String getCellValueAsString(Cell cell) {
		if (cell == null) {
			return "Ô dữ liệu trống!";
		}

		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue();
		case NUMERIC:
			return String.valueOf(cell.getNumericCellValue());
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		default:
			return "Lỗi! Không thể đọc dữ liệu";
		}
	}
}
