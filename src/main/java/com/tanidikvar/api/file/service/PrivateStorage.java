package com.tanidikvar.api.file.service;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.tanidikvar.api.common.error.DomainException;
@Service
public class PrivateStorage {
 private final Path root;
 public PrivateStorage(@Value("${app.storage-directory:.local/storage}") String root){this.root=Path.of(root).toAbsolutePath().normalize();}
 public void write(UUID id,byte[] bytes){try{Files.createDirectories(root);Files.write(root.resolve(id.toString()),bytes,StandardOpenOption.CREATE_NEW);}catch(IOException ex){throw unavailable();}}
 public byte[] read(String key){try{return Files.readAllBytes(root.resolve(UUID.fromString(key).toString()));}catch(IOException ex){throw unavailable();}}
 private DomainException unavailable(){return new DomainException(503,"STORAGE_UNAVAILABLE","Dosyaya şu anda erişilemiyor. Tekrar dene.");}
}

