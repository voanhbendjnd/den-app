package com.denhub.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.denhub.domain.User;
import com.denhub.security.CustomUserDetails;
import com.denhub.service.AuthService;
import com.denhub.service.MailService;
import com.denhub.service.UserService;
import com.denhub.service.dto.ResLoginDTO;
import com.denhub.service.errors.InvalidPasswordException;
import com.denhub.web.rest.vm.KeyAndPasswordVM;
import com.denhub.web.rest.vm.LoginVM;
import com.denhub.web.rest.vm.ManagedUserVM;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account", description = "API liên quan đến tài khoản và đăng nhập")
public class AccountResource {
    final UserService userService;
    final AuthenticationManagerBuilder authenticationManagerBuilder;
    final AuthService authService;
    final MailService mailService;
    final PasswordEncoder passwordEncoder;
    @Value("${denhub.jwt.refresh-token-validity-in-seconds}")
    private  Long refreshTokenExpiration;
    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Account resource request invalid")
    private static class AccountResourceException extends RuntimeException {
        private AccountResourceException(String message) {
            super(message);
        }
    }
    @GetMapping(path = "/activate")
    @Operation(summary = "Kích hoạt tài khoản", description = "Kích hoạt tài khoản người dùng thông qua mã xác nhận.")
    public void activateAccount(@RequestParam(value = "key") String key) {
        Optional<User> user = userService.activatedRegistration(key);
        if(user.isEmpty()){
            throw new AccountResourceException("No user was found for this activation key");
        }
    }
    @PostMapping(path = "/account/rest-password/init")
    @Operation(summary = "Yêu cầu khôi phục mật khẩu", description = "Gửi link reset mật khẩu vào email của người dùng.")
    public void requestPasswordReset(@RequestBody @Email @Size(min = 5, max = 254) String email){
        Optional<User> user = userService.requestPasswordReset(email);
        if(user.isPresent()){
            mailService.sendPasswordResetMail(user.orElseThrow());
        }
        else{
            log.warn("Password rest requested for non existing mail");
        }
    }

    @PostMapping(path = "/account/reset-password/finish")
    @Operation(summary = "Hoàn tất khôi phục mật khẩu", description = "Đặt lại mật khẩu mới dựa vào token reset.")
    public void finishPasswordRest(@RequestBody KeyAndPasswordVM keyAndPasswordVM){
        if(isPasswordLengthInvalid(keyAndPasswordVM.getNewPassword())){
            throw new InvalidPasswordException();
        }
        Optional<User> user = userService.completePasswordReset(keyAndPasswordVM.getNewPassword(), keyAndPasswordVM.getKey());
        if(user.isEmpty()){
            passwordEncoder.encode(keyAndPasswordVM.getNewPassword());
            throw new AccountResourceException("No user was found for this reset key");
        }
    }

    /*
    * vm: username, password
    * */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Xác thực người dùng và nhận về Access Token cùng Refresh Token (cookie).")
    public ResponseEntity<ResLoginDTO> loginWithEmail(@Valid @RequestBody LoginVM vm) {
        String normalizedEmail = vm.getUsername().trim().toLowerCase(Locale.ENGLISH);
        // check password
        UsernamePasswordAuthenticationToken userToken = new UsernamePasswordAuthenticationToken(normalizedEmail, vm.getPassword());

        try{
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(userToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.user();
            ResLoginDTO res = authService.generateResLoginDTO(user);
            ResponseCookie cookie = ResponseCookie.from("refresh_token", res.getRefreshToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration)
                    .sameSite("Strict")
                    .build();
            res.setRefreshToken(null);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(res);
        }
        catch(BadCredentialsException ex){
            throw new com.denhub.web.rest.errors.BadCredentialsException();
        }
    }
    private static boolean isPasswordLengthInvalid(String password) {
        return (
                StringUtils.isEmpty(password) ||
                        password.length() < ManagedUserVM.PASSWORD_MIN_LENGTH ||
                        password.length() > ManagedUserVM.PASSWORD_MAX_LENGTH
        );
    }
}
