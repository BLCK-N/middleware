package com.myrtle.middleware.dtos;

import com.myrtle.middleware.enums.ProofType;
import com.myrtle.middleware.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddHoldersDetailsDto {

    private String id;

    private String address;

    private List<String> proof;

    private ProofType proofType;

    private Status status;
}
