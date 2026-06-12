package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class MailConfigUpdateDto {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String fromAddress;
    private Boolean sslEnabled;
    private Boolean starttlsEnabled;
    private Boolean enabled;
    private Boolean devReturnCode;
}
