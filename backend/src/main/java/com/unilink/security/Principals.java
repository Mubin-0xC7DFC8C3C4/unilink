package com.unilink.security;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
public final class Principals {
  private Principals(){}
  public static class UnilinkUserPrincipal implements UserDetails {
    private final Long userId; private final String email; private final String passwordHash;
    private final Long universityId; private final Long departmentId; private final Long catalogId;
    private final String state; private final boolean active;
    public UnilinkUserPrincipal(Long userId,String email,String passwordHash,Long universityId,Long departmentId,Long catalogId,String state,boolean active){
      this.userId=userId; this.email=email; this.passwordHash=passwordHash; this.universityId=universityId;
      this.departmentId=departmentId; this.catalogId=catalogId; this.state=state; this.active=active; }
    public Long userId(){ return userId; } public Long universityId(){ return universityId; }
    public Long departmentId(){ return departmentId; } public Long catalogId(){ return catalogId; }
    public String state(){ return state; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){ return List.of(new SimpleGrantedAuthority("ROLE_STUDENT")); }
    @Override public String getPassword(){ return passwordHash; }
    @Override public String getUsername(){ return email; }
    @Override public boolean isAccountNonExpired(){ return true; }
    @Override public boolean isAccountNonLocked(){ return active && !"BANNED".equals(state) && !"SUSPENDED".equals(state); }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled(){ return active; }
  }
  public static class AdminPrincipal implements UserDetails {
    private final Long adminId; private final String email; private final String passwordHash;
    private final String role; private final Long universityId; private final boolean mfaVerified; private final boolean active;
    public AdminPrincipal(Long adminId,String email,String passwordHash,String role,Long universityId,boolean mfaVerified,boolean active){
      this.adminId=adminId; this.email=email; this.passwordHash=passwordHash; this.role=role;
      this.universityId=universityId; this.mfaVerified=mfaVerified; this.active=active; }
    public Long adminId(){ return adminId; } public String role(){ return role; }
    public Long universityId(){ return universityId; } public boolean mfaVerified(){ return mfaVerified; }
    public AdminPrincipal withMfa(){ return new AdminPrincipal(adminId,email,passwordHash,role,universityId,true,active); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){
      List<GrantedAuthority> a = new ArrayList<>();
      a.add(new SimpleGrantedAuthority("ROLE_"+role));
      if (mfaVerified) a.add(new SimpleGrantedAuthority("MFA_VERIFIED"));
      return a; }
    @Override public String getPassword(){ return passwordHash; }
    @Override public String getUsername(){ return email; }
    @Override public boolean isAccountNonExpired(){ return true; }
    @Override public boolean isAccountNonLocked(){ return active; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled(){ return active; }
  }
}
