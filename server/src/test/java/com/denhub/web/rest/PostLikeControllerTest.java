package com.denhub.web.rest;

import com.denhub.service.LikeService;
import com.denhub.service.dto.LikeResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostLikeControllerTest {

    @Mock
    private LikeService likeService;

    private PostLikeController postLikeController;

    @BeforeEach
    void setUp() {
        postLikeController = new PostLikeController(likeService);
    }

    @Test
    void toggleLike_ReturnsOkWithResponse() {
        LikeResponseDTO expectedDto = LikeResponseDTO.builder()
                .liked(true)
                .totalLikes(10L)
                .build();

        when(likeService.toggleLike(123L)).thenReturn(expectedDto);

        ResponseEntity<LikeResponseDTO> response = postLikeController.toggleLike(123L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isLiked()).isTrue();
        assertThat(response.getBody().getTotalLikes()).isEqualTo(10L);

        verify(likeService).toggleLike(123L);
    }
}
