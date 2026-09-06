package com.eeum.eeum.application.file;

public enum FileUploadPurpose {
    PROFILE("profiles"),
    STORE("stores"),
    PRODUCT("products"),
    USED("used"),
    COMMUNITY("community"),
    CHAT("chat");

    private final String prefix;

    FileUploadPurpose(String prefix) {
        this.prefix = prefix;
    }

    public String createObjectKey(Long accountId, String extension, String fileId) {
        return prefix + "/" + accountId + "/" + fileId + "." + extension;
    }

    public boolean owns(String objectKey, Long accountId) {
        return objectKey.startsWith(prefix + "/" + accountId + "/");
    }
}
