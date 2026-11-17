package com.unilink.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** I-16: CIDR/exact matching for the admin IP allow-list. */
class AdminIpCidrTest {
  @Test void exactIpMatches() {
    assertTrue(AdminAuthService.ipMatchesCidr("203.0.113.7", "203.0.113.7"));
    assertFalse(AdminAuthService.ipMatchesCidr("203.0.113.8", "203.0.113.7"));
  }

  @Test void cidrRangeMatches() {
    assertTrue(AdminAuthService.ipMatchesCidr("203.0.113.7", "203.0.113.0/24"));
    assertTrue(AdminAuthService.ipMatchesCidr("203.0.113.255", "203.0.113.0/24"));
    assertFalse(AdminAuthService.ipMatchesCidr("203.0.114.1", "203.0.113.0/24"));
    assertTrue(AdminAuthService.ipMatchesCidr("10.5.6.7", "10.0.0.0/8"));
    assertFalse(AdminAuthService.ipMatchesCidr("11.0.0.1", "10.0.0.0/8"));
  }

  @Test void cidrBoundaries() {
    assertTrue(AdminAuthService.ipMatchesCidr("192.168.1.1", "192.168.1.1/32"));
    assertFalse(AdminAuthService.ipMatchesCidr("192.168.1.2", "192.168.1.1/32"));
    assertTrue(AdminAuthService.ipMatchesCidr("8.8.8.8", "0.0.0.0/0"));
  }

  @Test void malformedInputNeverMatches() {
    assertFalse(AdminAuthService.ipMatchesCidr("not-an-ip", "203.0.113.0/24"));
    assertFalse(AdminAuthService.ipMatchesCidr("203.0.113.7", "203.0.113.0/33"));
    assertFalse(AdminAuthService.ipMatchesCidr("203.0.113.7", "203.0.113.0/abc"));
    assertFalse(AdminAuthService.ipMatchesCidr("203.0.113.7", "999.0.0.0/8"));
    assertFalse(AdminAuthService.ipMatchesCidr("::1", "203.0.113.0/24"));
  }
}
