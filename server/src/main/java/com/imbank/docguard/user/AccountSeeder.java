package com.imbank.docguard.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 역할별 계정을 미리 만듭니다 (SECURITY.md 2장 "미리 만드는 계정"). scripts/seed-accounts 가 이 기능을 켜서 실행합니다.
 * 비밀번호는 무작위로 만들어 {@code .local/accounts.txt}에만 적고, 화면·로그에는 출력하지 않습니다.
 * 이미 있는 계정은 건드리지 않습니다(비밀번호도 바꾸지 않음). 끝나면 서버를 종료합니다.
 */
@Component
@ConditionalOnProperty(name = "app.seed-accounts", havingValue = "true")
class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private record Seed(String email, String displayName, Role role) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("inspector@docguard.local", "점검 담당자", Role.INSPECTOR),
            new Seed("inspector2@docguard.local", "점검 담당자2", Role.INSPECTOR),
            new Seed("reviewer@docguard.local", "검토자", Role.REVIEWER),
            new Seed("admin@docguard.local", "관리자", Role.ADMIN));

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final ApplicationContext context;
    private final Path output;
    private final SecureRandom random = new SecureRandom();

    AccountSeeder(UserRepository users, PasswordEncoder passwordEncoder, TransactionTemplate tx,
                  ApplicationContext context, @Value("${app.seed-output:../.local/accounts.txt}") String output) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.context = context;
        this.output = Path.of(output).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> lines = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (users.existsByEmail(seed.email())) {
                log.info("이미 있는 계정이라 건너뜁니다: {}", seed.role());
                continue;
            }
            String password = randomPassword();
            tx.executeWithoutResult(status ->
                    users.save(new User(seed.email(), seed.displayName(), passwordEncoder.encode(password), seed.role())));
            lines.add(seed.role() + "\t" + seed.email() + "\t" + password);
        }
        if (!lines.isEmpty()) {
            write(lines);
            log.info("계정 {}개를 만들었습니다. 비밀번호는 {} 에 있습니다.", lines.size(), output);
        } else {
            log.info("새로 만든 계정이 없습니다. 기존 비밀번호는 {} 를 확인하세요.", output);
        }
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }

    private void write(List<String> lines) {
        try {
            Files.createDirectories(output.getParent());
            List<String> content = new ArrayList<>();
            content.add("# scripts/seed-accounts 가 " + LocalDateTime.now().withNano(0) + " 에 만든 계정 (로컬 전용, 저장소에 올리지 않음)");
            content.add("# 역할\t이메일\t비밀번호");
            content.addAll(lines);
            Files.write(output, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("계정 파일을 쓰지 못했습니다.", e);
        }
    }

    /** 16자, 영문 대소문자와 숫자를 모두 포함 (SECURITY.md 2장 규칙 충족) */
    String randomPassword() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String pw = sb.toString();
            if (pw.chars().anyMatch(Character::isDigit) && pw.chars().anyMatch(Character::isLetter)) {
                return pw;
            }
        }
    }
}
