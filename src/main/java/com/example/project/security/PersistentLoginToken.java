package com.example.project.security;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="legal_login_tokens",indexes=@Index(name="idx_login_tokens_username",columnList="username"))
public class PersistentLoginToken {
    @Id @Column(length=64) public String series;
    @Column(nullable=false,length=30) public String username;
    @Convert(converter=EncryptedTextConverter.class) @Column(nullable=false,columnDefinition="LONGTEXT") public String token;
    @Column(nullable=false) public Instant lastUsed;
}
