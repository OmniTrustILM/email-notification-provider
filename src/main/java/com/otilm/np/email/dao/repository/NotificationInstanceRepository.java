package com.otilm.np.email.dao.repository;

import com.otilm.np.email.dao.entity.NotificationInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationInstanceRepository extends JpaRepository<NotificationInstance, Long> {

    Optional<NotificationInstance> findByName(String name);

    Optional<NotificationInstance> findByUuid(UUID uuid);

}
