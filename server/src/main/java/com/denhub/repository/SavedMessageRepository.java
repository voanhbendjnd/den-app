package com.denhub.repository;
import com.denhub.domain.SavedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SavedMessageRepository extends JpaRepository<SavedMessage,Long> {
    Optional<SavedMessage> findByUserIdAndMessageId(Long userId,Long messageId);
}
