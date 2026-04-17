package com.evernex.bms.controller;

import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public AuthController(JdbcTemplate jdbc, JwtUtil jwt) {
        this.jdbc = jdbc;
        this.jwt = jwt;
    }

    private List<Long> allowedFactoryIds(long userId, String role) {
        if ("admin".equals(role)) {
            return jdbc.queryForList("SELECT factory_id FROM factories WHERE is_active=1", Long.class);
        }
        return jdbc.queryForList("SELECT factory_id FROM user_factories WHERE user_id=?", Long.class, userId);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || password == null) {
            throw new ApiException(400, "이메일과 비밀번호를 입력하세요.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM users WHERE email=?", email);
        if (rows.isEmpty() || !bcrypt.matches(password, (String) rows.get(0).get("password_hash"))) {
            throw new ApiException(401, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        Map<String, Object> u = rows.get(0);
        long uid = ((Number) u.get("user_id")).longValue();
        String role = (String) u.get("role");
        String name = (String) u.get("name");
        String token = jwt.sign(uid, role, email, name);
        List<Long> allowed = allowedFactoryIds(uid, role);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("user_id", uid);
        user.put("email", email);
        user.put("role", role);
        user.put("name", name);
        user.put("allowed_factory_ids", allowed);
        user.put("is_admin_scope", "admin".equals(role));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("user", user);
        return resp;
    }

    @PostMapping("/signup")
    public org.springframework.http.ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String name = body.get("name");
        if (email == null || password == null || name == null) {
            throw new ApiException(400, "이메일, 비밀번호, 이름을 모두 입력하세요.");
        }
        String emailTrim = email.trim().toLowerCase();
        String nameTrim = name.trim();
        if (!emailTrim.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ApiException(400, "올바른 이메일 형식이 아닙니다.");
        }
        if (password.length() < 7) {
            throw new ApiException(400, "비밀번호는 7자 이상이어야 합니다.");
        }
        if (nameTrim.isEmpty() || nameTrim.length() > 50) {
            throw new ApiException(400, "이름은 1~50자로 입력하세요.");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Integer.class, emailTrim);
        if (exists != null && exists > 0) throw new ApiException(409, "이미 가입된 이메일입니다.");

        String hash = bcrypt.encode(password);
        jdbc.update("INSERT INTO users (email,password_hash,role,name) VALUES (?,?,?,?)",
            emailTrim, hash, "operator", nameTrim);
        Long newId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);

        String token = jwt.sign(newId, "operator", emailTrim, nameTrim);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("user_id", newId);
        user.put("email", emailTrim);
        user.put("role", "operator");
        user.put("name", nameTrim);
        user.put("allowed_factory_ids", List.of());
        user.put("is_admin_scope", false);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("user", user);
        return org.springframework.http.ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var p = AuthContext.require();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", p.uid());
        user.put("role", p.role());
        user.put("email", p.email());
        user.put("name", p.name());
        user.put("allowed_factory_ids", p.allowedFactoryIds());
        user.put("is_admin_scope", p.adminScope());
        return Map.of("user", user);
    }
}
