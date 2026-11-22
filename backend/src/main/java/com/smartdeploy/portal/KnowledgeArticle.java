package com.smartdeploy.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Base de connaissances : un problème déjà résolu, réutilisable par tous les développeurs. */
@Entity
@Table(name = "knowledge_articles")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String symptoms;

    @Column(length = 2000)
    private String cause;

    @Column(nullable = false, length = 4000)
    private String solution;

    /** Signature de l'analyste à laquelle la fiche se rattache (code de cause), facultative. */
    @Column(length = 60)
    private String signature;

    @Column(name = "source_claim_id")
    private Long sourceClaimId;

    @Column(name = "author_email", length = 200)
    private String authorEmail;

    @Column(nullable = false)
    private int helpfulCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
