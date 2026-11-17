package com.unilink.identity.service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
/** Revokes every server session for a principal (password change, suspension, ban, revocation — D-013). */
@Service
public class SessionService {
  private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repoProvider;
  public SessionService(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repoProvider){ this.repoProvider=repoProvider; }
  public void invalidateAllSessions(String principalName){
    var repo = repoProvider.getIfAvailable();
    if (repo==null) return;
    repo.findByPrincipalName(principalName).keySet().forEach(repo::deleteById);
  }
}
