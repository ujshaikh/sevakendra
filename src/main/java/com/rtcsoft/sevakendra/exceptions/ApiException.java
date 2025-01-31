package com.rtcsoft.sevakendra.exceptions;

//This exception is thrown when attempting to retrieve a customer from the database, but the customer does not exist.
public class ApiException extends Exception {
	private static final long serialVersionUID = 1L;

	public ApiException(String message) {
		super(message);
	}
}
