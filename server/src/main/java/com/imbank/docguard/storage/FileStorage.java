package com.imbank.docguard.storage;

import com.imbank.docguard.common.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 원본 파일을 암호화해 {@code APP_STORAGE_DIR}에 둡니다. 저장 이름은 무작위이며, 올린 파일 이름은 경로에 쓰지 않습니다. (SECURITY.md 4장)
 */
@Service
public class FileStorage {

    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f-]{36}\\.bin");

    private final Path root;
    private final EncryptionService encryption;

    public FileStorage(AppProperties properties, EncryptionService encryption) {
        this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
        this.encryption = encryption;
    }

    /** 암호화해서 저장하고 저장 이름을 돌려줍니다. */
    public String save(byte[] content) {
        String name = UUID.randomUUID() + ".bin";
        try {
            Files.createDirectories(root);
            Files.write(resolve(name), encryption.encrypt(content));
        } catch (IOException e) {
            throw new UncheckedIOException("원본 파일을 저장하지 못했습니다.", e);
        }
        return name;
    }

    public byte[] read(String name) {
        try {
            return encryption.decrypt(Files.readAllBytes(resolve(name)));
        } catch (IOException e) {
            throw new UncheckedIOException("원본 파일을 읽지 못했습니다.", e);
        }
    }

    public void delete(String name) {
        if (name == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("원본 파일을 지우지 못했습니다.", e);
        }
    }

    private Path resolve(String name) {
        if (!STORED_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("저장 이름 형식이 올바르지 않습니다.");
        }
        return root.resolve(name);
    }
}
