package com.lisa.exception;

public class ExistedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ExistedException() {
	}

	public ExistedException(String message) {
		super(message);
	}

	public ExistedException(Throwable cause) {
		super(cause);
	}

	public ExistedException(String message, Throwable cause) {
		super(message, cause);
	}

}
