package com.denhub.web.rest;

import com.denhub.service.SearchUserService;
import com.denhub.service.projection.UsernameProjection;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/search")
public class SearchUserController {
    private final SearchUserService searchUserService;

    public SearchUserController(SearchUserService searchUserService) {
        this.searchUserService = searchUserService;
    }
    @GetMapping
    public ResponseEntity<List<UsernameProjection>> search(@RequestParam(name = "username", required = true) String username){
        return ResponseEntity.ok(
                searchUserService.searchByUsername(username));
    }
}
