package com.denhub.repository;
import com.denhub.domain.ChannelNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ChannelNotificationPreferenceRepository extends JpaRepository<ChannelNotificationPreference,Long> {
    Optional<ChannelNotificationPreference> findByUserIdAndChannelId(Long userId,Long channelId);
}
