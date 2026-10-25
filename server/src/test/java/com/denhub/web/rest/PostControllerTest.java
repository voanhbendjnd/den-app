package com.denhub.web.rest;

import com.denhub.service.PostService;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.service.dto.UpdatePostDTO;
import com.denhub.web.rest.vm.SizeDateIdVM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    private PostController postController;

    @BeforeEach
    void setUp() {
        postController = new PostController(postService);
    }

    @Test
    void getAllPostFromFriends_ReturnsOk() {
        SizeDateIdVM vm = new SizeDateIdVM();
        vm.setSize(10);
        ResultPaginationCursorDateId mockResult = new ResultPaginationCursorDateId();

        when(postService.getAllPost(10, null, null)).thenReturn(mockResult);

        ResponseEntity<ResultPaginationCursorDateId> response = postController.getAllPostFromFriends(vm);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(mockResult);
        verify(postService).getAllPost(10, null, null);
    }

    @Test
    void updatePost_ReturnsOkWithUpdatedPost() {
        UpdatePostDTO dto = UpdatePostDTO.builder()
                .content("Updated content")
                .privacy("PUBLIC")
                .build();

        DisplayPostDTO expected = DisplayPostDTO.builder()
                .id(100L)
                .content("Updated content")
                .privacy("PUBLIC")
                .build();

        when(postService.updatePost(100L, dto)).thenReturn(expected);

        ResponseEntity<DisplayPostDTO> response = postController.updatePost(100L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEqualTo("Updated content");
        verify(postService).updatePost(100L, dto);
    }

    @Test
    void deletePost_ReturnsNoContent() {
        ResponseEntity<Void> response = postController.deletePost(100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(postService).deletePost(100L);
    }
}
