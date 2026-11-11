package com.denhub.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void testNoArgsConstructor() {
        Message message = new Message();
        assertThat(message.getId()).isNull();
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        Message message = new Message(
            1L,
            10L,
            100L,
            100L,
            20L,
            "TEXT",
            "Hello World",
            "http://example.com/file.png",
            "file.png",
            1024L,
            800,
            600,
            now
        );

        assertThat(message.getId()).isEqualTo(1L);
        assertThat(message.getChannelId()).isEqualTo(10L);
        assertThat(message.getAuthorId()).isEqualTo(100L);
        assertThat(message.getSenderId()).isEqualTo(100L);
        assertThat(message.getConversationId()).isEqualTo(20L);
        assertThat(message.getType()).isEqualTo("TEXT");
        assertThat(message.getContent()).isEqualTo("Hello World");
        assertThat(message.getMediaUrl()).isEqualTo("http://example.com/file.png");
        assertThat(message.getFileName()).isEqualTo("file.png");
        assertThat(message.getFileSize()).isEqualTo(1024L);
        assertThat(message.getWidth()).isEqualTo(800);
        assertThat(message.getHeight()).isEqualTo(600);
        assertThat(message.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void testBuilder() {
        Message message = Message.builder()
            .id(2L)
            .content("Builder test")
            .type("TEXT")
            .channelId(5L)
            .authorId(99L)
            .build();

        assertThat(message.getId()).isEqualTo(2L);
        assertThat(message.getContent()).isEqualTo("Builder test");
        assertThat(message.getType()).isEqualTo("TEXT");
        assertThat(message.getChannelId()).isEqualTo(5L);
        assertThat(message.getAuthorId()).isEqualTo(99L);
        assertThat(message.getSenderId()).isEqualTo(99L);
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void testSenderIdFallback() {
        Message message = new Message();
        message.setAuthorId(42L);
        assertThat(message.getSenderId()).isEqualTo(42L);

        message.setSenderId(99L);
        assertThat(message.getSenderId()).isEqualTo(99L);
    }
}
