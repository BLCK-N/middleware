package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.DeviceDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceDetailsRepository extends JpaRepository<DeviceDetails, String> {

    Optional<DeviceDetails> findByUser_Id(String userId);

    Optional<DeviceDetails> findByIdAndUser_Id(String id, String userId);
}
