package com.lisa.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lisa.user.User;
import com.lisa.user.UserRepository;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
@Tag(name = "Auth Endpoint")
public class AuthenticationController {
	private final UserRepository userRepo;
	private final AuthenticationService service;

	@PostMapping("/validation")
	public ResponseEntity<String> getUserIsActivated(@RequestBody ValidationRequest request) {
		Optional<User> user = userRepo.findByEmail(request.getEmail());
		try {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

			if (encoder.matches(request.getPassword(), user.get().getPassword())) {
				if (user.get().isMfaEnabled()) {
					TimeProvider timeProvider = new SystemTimeProvider();
					CodeGenerator codeGenerator = new DefaultCodeGenerator();
					DefaultCodeVerifier verify = new DefaultCodeVerifier(codeGenerator, timeProvider);
					verify.setAllowedTimePeriodDiscrepancy(0);
					if (verify.isValidCode(user.get().getSecret(), request.getCode())) {
						return ResponseEntity.ok().body("success");
					} else {
						return ResponseEntity.ok().body("Wrong 2FA");
					}
				} else {
					return ResponseEntity.ok().body("success");
				}

			} else {
				return ResponseEntity.ok().body("Password is not correct");
			}
		} catch (Exception e) {
			return ResponseEntity.ok().body("Username is not exist");
		}
	}

	@PostMapping("/register")
	public ResponseEntity<AuthenticationResponse> register(@RequestBody RegisterRequest request) {
		return ResponseEntity.ok(service.register(request));
	}

	@PostMapping("/authenticate")
	public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
		return ResponseEntity.ok(service.authenticate(request));
	}
	
	@PostMapping("/getCode")
	public ResponseEntity<String> getCode(@RequestBody GetCodeRequest request) {
		return ResponseEntity.ok(service.generateCode(request.getEmail()));
	}
	
	@PostMapping("/forgot-password")
	public ResponseEntity<String> forgotPassword(@RequestBody ForgotPassRequest request) {
		return ResponseEntity.ok(service.forgotPassword(request));
	}

	@PostMapping("/refresh-token")
	public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
		service.refreshToken(request, response);
	}

	@PostMapping("/logout")
	public ResponseEntity<String> logout(@RequestBody LogoutRequest request) {
		return ResponseEntity.ok(service.logout(request.getAccess_token()));
	}

	@GetMapping("/test/{email}")
	public ResponseEntity<HashMap<String, List<String>>> getNetworks(@PathVariable("email") String email) {
		HashMap<String, List<String>> networks = new HashMap<>();
		List<String> f0 = new ArrayList<>();
		Optional<User> userF0 = userRepo.findByEmail(email);
		if (userF0.isPresent()) {
			f0.add(userF0.get().getEmail());
		} else {
			throw new RuntimeException("Tài khoản không tồn tại!");
		}

		List<User> userF1 = userRepo.findAllByRefferal(userF0.get().getEmail());
		List<String> f1 = new ArrayList<>();
		for (User item : userF1) {
			f1.add(item.getEmail());
		}

		List<String> f2 = new ArrayList<>();
		if (userF1.size() > 0) {
			for (User item : userF1) {
				List<User> userF2 = userRepo.findAllByRefferal(item.getEmail());
				if (userF2.size() > 0) {
					for (User item2 : userF2) {
						f2.add(item2.getEmail());
					}
				}
			}
		}

		List<String> f3 = new ArrayList<>();
		for (String item : f2) {
			List<User> userF3 = userRepo.findAllByRefferal(item);
			if (userF3.size() > 0) {
				for (User itemF3 : userF3) {
					f3.add(itemF3.getEmail());
				}
			}
		}

		List<String> f4 = new ArrayList<>();
		for (String item : f3) {
			List<User> userF4 = userRepo.findAllByRefferal(item);
			if (userF4.size() > 0) {
				for (User itemF4 : userF4) {
					f4.add(itemF4.getEmail());
				}
			}
		}

		List<String> f5 = new ArrayList<>();
		for (String item : f4) {
			List<User> userF5 = userRepo.findAllByRefferal(item);
			if (userF5.size() > 0) {
				for (User itemF5 : userF5) {
					f5.add(itemF5.getEmail());
				}
			}
		}

		networks.put("F0", f0);
		networks.put("F1", f1);
		networks.put("F2", f2);
		networks.put("F3", f3);
		networks.put("F4", f4);
		networks.put("F5", f5);

		return ResponseEntity.ok(networks);
	}

	@PostMapping("/update-ref")
	public ResponseEntity<UpdateRefResponse> updateRef(@RequestBody UpdateRefRequest request) {
		return ResponseEntity.ok(service.updateRef(request.getCurrent(), request.getCode()));
	}

	@PostMapping("/update-exness")
	public ResponseEntity<UpdateRefResponse> updateExness(@RequestBody UpdateExnessRequest request) {
		return ResponseEntity.ok(service.updateExness(request.getEmail(), request.getExness(), request.getType()));
	}

	@PostMapping("/check-ref")
	public ResponseEntity<Integer> checkRef(@RequestBody RefferalRequest request) {
		return ResponseEntity.ok(service.checkRef(request.getCode()));
	}
}
