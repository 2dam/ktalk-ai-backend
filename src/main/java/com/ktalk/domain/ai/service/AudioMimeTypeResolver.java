package com.ktalk.domain.ai.service;

final class AudioMimeTypeResolver {

    private AudioMimeTypeResolver() {
    }

    static String resolveMimeType(String filename, String contentType) {
        if (contentType != null && contentType.startsWith("audio/")) {
            return contentType;
        }
        if (filename != null) {
            if (filename.endsWith(".mp3")) return "audio/mp3";
            if (filename.endsWith(".wav")) return "audio/wav";
            if (filename.endsWith(".ogg")) return "audio/ogg";
            if (filename.endsWith(".m4a")) return "audio/m4a";
            if (filename.endsWith(".webm")) return "audio/webm";
        }
        return "audio/webm"; // 브라우저 기본 녹음 포맷
    }
}
