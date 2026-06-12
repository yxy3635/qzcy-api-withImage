package com.qzcy.backend.service;

import com.qzcy.backend.dto.MailConfigDto;
import com.qzcy.backend.dto.MailConfigUpdateDto;
import com.qzcy.backend.entity.MailConfig;

public interface MailConfigService {
    MailConfig current();
    MailConfigDto adminDetail();
    MailConfigDto update(MailConfigUpdateDto dto);
}
