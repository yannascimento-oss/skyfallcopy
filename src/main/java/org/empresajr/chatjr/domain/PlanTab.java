package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Uma etapa (aba) do plano de um cliente. */
@Entity
@Table(name = "plan_tab")
public class PlanTab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String html = "";

    @Column(nullable = false)
    private boolean published;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "what_is_it")
    private String whatIsIt;

    private String objective;

    @Column(name = "key_points")
    private String keyPoints;

    @Column(name = "suggested_questions")
    private String suggestedQuestions;

    @Column(name = "source_label")
    private String sourceLabel;

    @Column(name = "content_updated_at")
    private Instant contentUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanTab() {
    }

    public PlanTab(Long clientId, String slug, String name, int sortOrder, Instant createdAt) {
        this.clientId = clientId;
        this.slug = slug;
        this.name = name;
        this.title = name;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static String join(List<String> items) {
        if (items == null) {
            return null;
        }
        List<String> clean = items.stream().filter(Objects::nonNull)
                .map(s -> s.replaceAll("\\s+", " ").trim()).filter(s -> !s.isEmpty()).toList();
        return clean.isEmpty() ? null : String.join("\n", clean);
    }

    public boolean hasSameContent(String title, String html, String shortDescription, String whatIsIt,
                                  String objective, String keyPoints, String suggestedQuestions, String source) {
        return Objects.equals(this.title, title) && Objects.equals(this.html, html)
                && Objects.equals(this.shortDescription, shortDescription) && Objects.equals(this.whatIsIt, whatIsIt)
                && Objects.equals(this.objective, objective) && Objects.equals(this.keyPoints, keyPoints)
                && Objects.equals(this.suggestedQuestions, suggestedQuestions)
                && Objects.equals(this.sourceLabel, source);
    }

    public void applyContent(String title, String html, String shortDescription, String whatIsIt,
                             String objective, String keyPoints, String suggestedQuestions, String source,
                             Instant now) {
        this.title = title;
        this.html = html == null ? "" : html;
        this.shortDescription = shortDescription;
        this.whatIsIt = whatIsIt;
        this.objective = objective;
        this.keyPoints = keyPoints;
        this.suggestedQuestions = suggestedQuestions;
        this.sourceLabel = source;
        this.contentVersion++;
        this.contentUpdatedAt = now;
    }

    public void applyDefinition(String shortDescription, String whatIsIt, String objective) {
        this.shortDescription = shortDescription;
        this.whatIsIt = whatIsIt;
        this.objective = objective;
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public String getHtml() { return html; }
    public boolean isPublished() { return published; }
    public int getContentVersion() { return contentVersion; }
    public String getShortDescription() { return shortDescription; }
    public String getWhatIsIt() { return whatIsIt; }
    public String getObjective() { return objective; }
    public String getKeyPoints() { return keyPoints; }
    public String getSuggestedQuestions() { return suggestedQuestions; }
    public String getSourceLabel() { return sourceLabel; }
    public Instant getContentUpdatedAt() { return contentUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPublished(boolean published) { this.published = published; }
}
