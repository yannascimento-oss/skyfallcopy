package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Estado anterior de uma aba, guardado antes de cada alteração de conteúdo. */
@Entity
@Table(name = "plan_version")
public class PlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tab_id", nullable = false)
    private Long tabId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String html;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "what_is_it")
    private String whatIsIt;

    private String objective;

    @Column(name = "key_points")
    private String keyPoints;

    @Column(name = "suggested_questions")
    private String suggestedQuestions;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanVersion() {
    }

    public static PlanVersion snapshotOf(PlanTab tab, String reason, String createdBy, Instant now) {
        PlanVersion v = new PlanVersion();
        v.tabId = tab.getId();
        v.version = tab.getContentVersion();
        v.title = tab.getTitle();
        v.html = tab.getHtml();
        v.shortDescription = tab.getShortDescription();
        v.whatIsIt = tab.getWhatIsIt();
        v.objective = tab.getObjective();
        v.keyPoints = tab.getKeyPoints();
        v.suggestedQuestions = tab.getSuggestedQuestions();
        v.reason = reason;
        v.createdBy = createdBy;
        v.createdAt = now;
        return v;
    }

    public Long getId() { return id; }
    public Long getTabId() { return tabId; }
    public int getVersion() { return version; }
    public String getTitle() { return title; }
    public String getHtml() { return html; }
    public String getShortDescription() { return shortDescription; }
    public String getWhatIsIt() { return whatIsIt; }
    public String getObjective() { return objective; }
    public String getKeyPoints() { return keyPoints; }
    public String getSuggestedQuestions() { return suggestedQuestions; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
