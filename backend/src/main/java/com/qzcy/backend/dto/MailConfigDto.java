package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MailConfigDto {
    private Long id;
    private String host;
    private Integer port;
    private String username;
    private String fromAddress;
    private Boolean sslEnabled;
    private Boolean starttlsEnabled;
    private Boolean enabled;
    private Boolean devReturnCode;
    private Boolean passwordConfigured;
}
