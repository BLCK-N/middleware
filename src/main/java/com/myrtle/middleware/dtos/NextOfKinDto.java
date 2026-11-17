package com.myrtle.middleware.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NextOfKinDto {
	private String id;
	private String userId;
	private String fullName;
	private String relationship;
	private String phoneNumber;
	private String email;
	private String address;
	private boolean primary;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
