package org.empresajr.chatjr.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.empresajr.chatjr.domain.PlanTab;

import java.time.Instant;
import java.util.List;

/** Visão de uma aba. Campos de gestão (published, allowed) só aparecem para a consultoria. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TabView(Long id, String slug, String name, String title, Integer sortOrder,
                      String shortDescription, String whatIsIt, String objective,
                      List<String> keyPoints, List<String> suggestedQuestions, String source,
                      Instant contentUpdatedAt, Integer contentVersion, String html,
                      Boolean published, Boolean allowed) {

    public static TabView of(PlanTab t, boolean withHtml, Boolean published, Boolean allowed) {
        return new TabView(t.getId(), t.getSlug(), t.getName(), t.getTitle(), t.getSortOrder(),
                t.getShortDescription(), t.getWhatIsIt(), t.getObjective(),
                PlanTab.split(t.getKeyPoints()), PlanTab.split(t.getSuggestedQuestions()), t.getSourceLabel(),
                t.getContentUpdatedAt(), t.getContentVersion(), withHtml ? t.getHtml() : null, published, allowed);
    }
}
