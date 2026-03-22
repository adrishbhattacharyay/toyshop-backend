package com.toyshop.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String rawPassword = "admin123";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("BCrypt Hash for '" + rawPassword + "': " + encodedPassword);
        System.out.println("Matches: " + encoder.matches(rawPassword, encodedPassword));
    }
}