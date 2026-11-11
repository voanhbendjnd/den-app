package com.denhub.repository;
import com.denhub.domain.MessagePin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MessagePinRepository extends JpaRepository<MessagePin,Long> {
    Optional<MessagePin> findByMessageId(Long messageId);
}
