package com.example.ersh.guessme;

public final class SftpAccessInfo {
    public static final String SFTP_PRIVATE_KEY = "-";

    public static final String SFTP_PASS = "-";
    public static final String SFTP_USER = "-";
    public static final String SFTP_HOST = "-";
    public static final int SFTP_PORT = 22;

    private SftpAccessInfo() {
        throw new AssertionError();
    }
}
