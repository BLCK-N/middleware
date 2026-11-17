package com.myrtle.middleware.dtos.request;

public record ChangePassword (
        String oldPassword,
        String newPassword
){
}
