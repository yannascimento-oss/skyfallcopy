package org.empresajr.chatjr.web.dto;

import java.time.Instant;
import java.util.List;

public record ExportView(String company, String segment, Instant exportedAt, int planVersion, List<TabView> tabs) {
}
