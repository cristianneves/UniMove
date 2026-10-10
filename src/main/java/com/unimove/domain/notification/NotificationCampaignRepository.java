package com.unimove.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, UUID> {

    Page<NotificationCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
