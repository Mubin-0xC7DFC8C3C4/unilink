package com.unilink.integration.sms;
public interface SmsProvider {
  void sendOtp(String phone, String otp) throws ProviderException;
  class ProviderException extends RuntimeException { public ProviderException(String m){ super(m); } public ProviderException(String m, Throwable t){ super(m,t); } }
}
