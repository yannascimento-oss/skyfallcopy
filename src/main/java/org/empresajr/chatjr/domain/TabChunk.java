package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Trecho do texto de uma aba, usado pela busca por relevância do chat. */
@Entity
@Table(name = "tab_chunk")
public class TabChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tab_id", nullable = false)
    private Long tabId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private String content;

    protected TabChunk() {
    }

    public TabChunk(Long tabId, int chunkIndex, String content) {
        this.tabId = tabId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getTabId() { return tabId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
}
