package com.tanidikvar.api.auth.service;
import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.repository.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class AccountLifecycleService {
 private final AccountRepository accounts; private final AuthSessionRepository sessions; private final ActionTokenRepository tokens;
 public AccountLifecycleService(AccountRepository accounts,AuthSessionRepository sessions,ActionTokenRepository tokens){this.accounts=accounts;this.sessions=sessions;this.tokens=tokens;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void setDisabled(Account account,boolean disabled,Instant now){
  if(disabled){
   account.revokeAdmin(now);sessions.revokeAll(account.getId(),now);
   for(var purpose:ActionPurpose.values())tokens.consumeAll(account.getId(),purpose,now);
  }
  account.setDisabled(disabled,now);accounts.flush();
 }
}
