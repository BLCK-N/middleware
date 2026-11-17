package com.myrtle.middleware.dtos.response;

import java.util.List;

public class BankListResponse {
    private String status;
    private String message;
    private List<Bank> data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Bank> getData() {
        return data;
    }

    public void setData(List<Bank> data) {
        this.data = data;
    }

}

class Bank {
    private int id;
    private String code;
    private String name;
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}