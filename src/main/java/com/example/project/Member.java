package com.example.project;
import jakarta.persistence.*;
@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String username;
    @Column(nullable = false, length = 100) private String password;
    @Column(nullable=false) private boolean admin = false;
    public boolean isAdmin() { return admin; }
    void makeAdmin() { admin=true; }
    protected Member() {}
    public Member(String username, String password) { this.username = username; this.password = password; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}