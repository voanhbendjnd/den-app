package com.denhub.security;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private DomainUserDetailsService domainUserDetailsService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        domainUserDetailsService = new DomainUserDetailsService(userRepository);

        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);

        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setEmail("test@denhub.com");
        activeUser.setPassword("hashed_password");
        activeUser.setActivated(true);
        activeUser.setAuthorities(Set.of(authority));
    }

    @Test
    void loadUserByUsername_ValidEmail_ReturnsUserDetails() {
        when(userRepository.findOneWithAuthoritiesByEmail("test@denhub.com"))
                .thenReturn(Optional.of(activeUser));

        UserDetails userDetails = domainUserDetailsService.loadUserByUsername("TEST@DENHUB.COM");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("test@denhub.com");
        verify(userRepository).findOneWithAuthoritiesByEmail("test@denhub.com");
    }

    @Test
    void loadUserByUsername_NonExistentEmail_ThrowsException() {
        when(userRepository.findOneWithAuthoritiesByEmail("notfound@denhub.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainUserDetailsService.loadUserByUsername("notfound@denhub.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    void loadUserByUsername_UserNotActivated_ThrowsUserNotActivatedException() {
        activeUser.setActivated(false);
        when(userRepository.findOneWithAuthoritiesByEmail("test@denhub.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> domainUserDetailsService.loadUserByUsername("test@denhub.com"))
                .isInstanceOf(UserNotActivatedException.class)
                .hasMessageContaining("was not activated");
    }
}
