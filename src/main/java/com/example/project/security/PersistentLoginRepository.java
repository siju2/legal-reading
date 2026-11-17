package com.example.project.security;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.web.authentication.rememberme.*;
import java.time.Instant;
import java.util.Date;
@Repository
public class PersistentLoginRepository implements PersistentTokenRepository {
    @PersistenceContext private EntityManager em;
    @Override @Transactional public void createNewToken(PersistentRememberMeToken token){
        em.createQuery("delete from PersistentLoginToken t where t.lastUsed < :cutoff").setParameter("cutoff",Instant.now().minusSeconds(14*24*60*60)).executeUpdate();
        var row=new PersistentLoginToken();row.series=token.getSeries();row.username=token.getUsername();row.token=token.getTokenValue();row.lastUsed=token.getDate().toInstant();em.persist(row);em.flush();
    }
    @Override @Transactional public void updateToken(String series,String token,Date used){
        var row=em.find(PersistentLoginToken.class,series);if(row==null)throw new org.springframework.dao.DataRetrievalFailureException("Remember-me token no longer exists");
        row.token=token;row.lastUsed=used.toInstant();em.flush();
    }
    @Override @Transactional(readOnly=true) public PersistentRememberMeToken getTokenForSeries(String series){
        var row=em.find(PersistentLoginToken.class,series);return row==null?null:new PersistentRememberMeToken(row.username,row.series,row.token,Date.from(row.lastUsed));
    }
    @Override @Transactional public void removeUserTokens(String username){em.createQuery("delete from PersistentLoginToken t where t.username=:user").setParameter("user",username).executeUpdate();}
}
