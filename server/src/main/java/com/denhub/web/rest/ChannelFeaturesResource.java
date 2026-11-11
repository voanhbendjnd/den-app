package com.denhub.web.rest;
import com.denhub.service.ChannelFeaturesService;
import com.denhub.service.dto.*;
import com.denhub.util.anotation.ApiMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChannelFeaturesResource {
    private final ChannelFeaturesService service;
    public ChannelFeaturesResource(ChannelFeaturesService service) { this.service=service; }
    @GetMapping("/channels/{channelId}/messages/search")
    @ApiMessage("Kết quả tìm kiếm tin nhắn")
    public CursorPageResponse<MessageResponse> search(@PathVariable("channelId") Long channel,
            @RequestParam("q") String query,@RequestParam(value="before",required=false) Long before,
            @RequestParam(value="limit",defaultValue="30") Integer limit) { return service.search(channel,query,before,limit); }
    @GetMapping("/channels/{channelId}/pins")
    @ApiMessage("Danh sách tin ghim")
    public CursorPageResponse<MessageResponse> pins(@PathVariable("channelId") Long channel,
            @RequestParam(value="before",required=false) Long before,@RequestParam(value="limit",defaultValue="30") Integer limit) { return service.pins(channel,before,limit); }
    @GetMapping("/messages/saved")
    @ApiMessage("Danh sách tin đã lưu")
    public CursorPageResponse<MessageResponse> saved(@RequestParam(value="before",required=false) Long before,
            @RequestParam(value="limit",defaultValue="30") Integer limit) { return service.saved(before,limit); }
    @PutMapping("/messages/{messageId}/pin")
    public void pin(@PathVariable("messageId") Long id) { service.pin(id,true); }
    @DeleteMapping("/messages/{messageId}/pin")
    public void unpin(@PathVariable("messageId") Long id) { service.pin(id,false); }
    @PutMapping("/messages/{messageId}/saved")
    public void save(@PathVariable("messageId") Long id) { service.save(id,true); }
    @DeleteMapping("/messages/{messageId}/saved")
    public void unsave(@PathVariable("messageId") Long id) { service.save(id,false); }
    @GetMapping("/channels/{channelId}/notification-preference")
    @ApiMessage("Lựa chọn thông báo kênh")
    public ChannelFeaturesService.Preference preference(@PathVariable("channelId") Long channel) { return service.preference(channel); }
    @PutMapping("/channels/{channelId}/notification-preference")
    @ApiMessage("Đã lưu lựa chọn thông báo")
    public ChannelFeaturesService.Preference preference(@PathVariable("channelId") Long channel,@Valid @RequestBody PreferenceRequest body) { return service.preference(channel,body.muted()); }
    public record PreferenceRequest(@NotNull Boolean muted) {}
}
