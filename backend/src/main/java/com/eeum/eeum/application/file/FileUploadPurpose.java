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

    public String createTemporaryObjectKey(Long accountId, String extension, String fileId) {
        return "tmp/" + prefix + "/" + accountId + "/" + fileId + "." + extension;
    }

    public boolean owns(String objectKey, Long accountId) {
        return objectKey.startsWith(prefix + "/" + accountId + "/");
    }

    public boolean ownsTemporary(String objectKey, Long accountId) {
        return objectKey.startsWith("tmp/" + prefix + "/" + accountId + "/");
    }

    public static FileUploadPurpose findTemporaryPurpose(String objectKey, Long accountId) {
        return java.util.Arrays.stream(values())
                .filter(purpose -> purpose.ownsTemporary(objectKey, accountId))
                .findFirst()
                .orElse(null);
    }

    public static boolean isFinalObjectKey(String objectKey) {
        return java.util.Arrays.stream(values())
                .anyMatch(purpose -> objectKey.startsWith(purpose.prefix + "/"));
    }
}
