package com.example.web;

import org.springframework.http.HttpStatus;

/**
 * An error meant for the user. `code` is the machine-readable string the
 * frontend's ApiError carries, so screens can react to a specific case
 * without matching on English text.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
