package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
